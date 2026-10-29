package org.hibernate.accessor.testing.aot.asm;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.hibernate.accessor.asm.aot.AotConstructorMetadata;
import org.hibernate.accessor.asm.aot.AotFieldMetadata;
import org.hibernate.accessor.asm.aot.AotMemberMetadata;
import org.hibernate.accessor.asm.aot.AotMethodMetadata;
import org.hibernate.accessor.asm.aot.AotParameterMetadata;
import org.hibernate.accessor.asm.aot.AotTypeMetadata;
import org.hibernate.accessor.asm.aot.AsmAotGenerator;
import org.hibernate.accessor.asm.aot.GenerationNames;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Internal build/test support; not a published API.
 * Build-tool-independent processing of an unpacked class directory. Selected hosts expose
 * instance fields (final fields are read-only), zero-argument non-void methods, one-argument
 * methods, and constructors of concrete non-enum classes. No input classes are loaded.
 * Callers own host selection, runtime hooks, output cleanup and build dependency tracking.
 * Inputs must be unenhanced; generated dispatch names are reserved by the generator.
 */
public final class AsmAotProcessor {
	private final AsmAotGenerator generator;

	public AsmAotProcessor(GenerationNames names) {
		generator = new AsmAotGenerator( names );
	}

	/** Copies resources and classes, transforms selected hosts, and emits the generated factory.
	 * Input and output directories must be disjoint. Output must be empty to avoid stale classes.
	 */
	public void process(Path input, Path output, Predicate<String> selectHost, boolean withFallback) throws IOException {
		input = input.toAbsolutePath().normalize();
		output = output.toAbsolutePath().normalize();
		if ( input.startsWith( output ) || output.startsWith( input ) ) {
			throw new IllegalArgumentException( "Input and output directories must be disjoint" );
		}
		if ( Files.exists( output ) ) {
			try ( var entries = Files.list( output ) ) {
				if ( entries.findAny().isPresent() ) {
					throw new IllegalArgumentException( "Output directory must be empty" );
				}
			}
		}
		var classes = new LinkedHashMap<String, byte[]>();
		try ( var paths = Files.walk( input ) ) {
			for ( Path path : paths.filter( Files::isRegularFile ).sorted().toList() ) {
				Path relative = input.relativize( path );
				String name = relative.toString().replace( File.separatorChar, '/' );
				if ( name.endsWith( ".class" ) ) {
					classes.put( name.substring( 0, name.length() - 6 ).replace( '/', '.' ), Files.readAllBytes( path ) );
				}
				else {
					Path target = output.resolve( relative );
					Files.createDirectories( target.getParent() );
					Files.copy( path, target );
				}
			}
		}
		List<AotTypeMetadata> hosts = new ArrayList<>();
		for ( var entry : classes.entrySet() ) {
			String name = entry.getKey();
			if ( name.equals( "module-info" )
					|| name.equals( "package-info" )
					|| name.endsWith( ".package-info" ) || !selectHost.test( name ) )
				continue;
			ClassReader reader = new ClassReader( entry.getValue() );
			AotTypeMetadata host = new AotTypeMetadata( name.contains( "." ) ? name.substring( 0, name.lastIndexOf( '.' ) ) : "", name, name,
					( reader.getAccess() & Opcodes.ACC_PUBLIC ) != 0, ( reader.getAccess() & Opcodes.ACC_INTERFACE ) != 0 );
			Set<AotMemberMetadata> readers = new HashSet<>(), writers = new HashSet<>();
			Set<AotConstructorMetadata> constructors = new HashSet<>();
			int hostIndex = hosts.size();
			// Collect access requests before transforming field declarations.
			reader.accept( new ClassVisitor( Opcodes.ASM9 ) {
				@Override
				public FieldVisitor visitField(int access, String field, String descriptor, String signature, Object value) {
					if ( ( access & Opcodes.ACC_STATIC ) == 0 ) {
						AotFieldMetadata member = new AotFieldMetadata( field, descriptor, primitive( Type.getType( descriptor ) ), name );
						readers.add( member );
						if ( ( access & Opcodes.ACC_FINAL ) == 0 )
							writers.add( member );
					}
					return super.visitField( access, field, descriptor, signature, value );
				}

				@Override
				public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
					Type[] arguments = Type.getArgumentTypes( descriptor );
					Type result = Type.getReturnType( descriptor );
					if ( method.equals( "<init>" ) && ( reader.getAccess() & ( Opcodes.ACC_ABSTRACT | Opcodes.ACC_ENUM ) ) == 0 ) {
						List<AotParameterMetadata> parameters = new ArrayList<>();
						for ( Type argument : arguments )
							parameters.add( new AotParameterMetadata( "arg", argument.getDescriptor(), primitive( argument ) ) );
						constructors.add( new AotConstructorMetadata( name, name, descriptor, parameters ) );
					}
					else if ( !method.startsWith( "<" ) && ( access & Opcodes.ACC_STATIC ) == 0 ) {
						if ( arguments.length == 0 && result.getSort() != Type.VOID )
							readers.add( new AotMethodMetadata( method,
									descriptor, primitive( result ), name, host.isInterface(), result.getDescriptor() ) );
						if ( arguments.length == 1 )
							writers.add( new AotMethodMetadata( method, descriptor, primitive( arguments[0] ),
									name, host.isInterface(), result.getDescriptor() ) );
					}
					return super.visitMethod( access, method, descriptor, signature, exceptions );
				}

			}, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES );
			// Passing the reader lets ASM copy unchanged methods and their frames directly. Only injected
			// methods need frame computation, without loading application classes during transformation.
			ClassWriter transformed = new ClassWriter( reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS );
			reader.accept( generator.hostVisitor( transformed, host, readers, writers, constructors, hostIndex ), 0 );
			entry.setValue( transformed.toByteArray() );
			hosts.add( host );
		}
		for ( var entry : generator.generateClasses( hosts, withFallback ).entrySet() ) {
			if ( classes.putIfAbsent( entry.getKey(), entry.getValue() ) != null ) {
				throw new IllegalArgumentException( "Generated class collides with input: " + entry.getKey() );
			}
		}
		for ( var entry : classes.entrySet() ) {
			Path target = output.resolve( entry.getKey().replace( '.', '/' ) + ".class" );
			Files.createDirectories( target.getParent() );
			Files.write( target, entry.getValue() );
		}
	}

	private static boolean primitive(Type type) {
		return type.getSort() >= Type.BOOLEAN && type.getSort() <= Type.DOUBLE;
	}
}
