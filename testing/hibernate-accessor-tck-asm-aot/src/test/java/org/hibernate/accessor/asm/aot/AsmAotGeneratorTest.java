package org.hibernate.accessor.asm.aot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.GENERATED_CLASS_VERSION;

import java.io.File;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.hibernate.accessor.AccessorFactory;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

class AsmAotGeneratorTest {
	@TempDir
	Path temporary;

	@ParameterizedTest
	@ValueSource(ints = { 256, 64 })
	void generatedCodeRunsWithoutGeneratorDependencies(int chunkSize) throws Exception {
		Path hosts = temporary.resolve( "hosts" );
		Path application = temporary.resolve( "application" );
		GenerationNames names = new GenerationNames( "generated.Factory", "generated.Reader", "generated.Writer",
				"generated.Instantiator", RuntimeHooks.class.getName() );
		AsmAotGenerator generator = new AsmAotGenerator( names,
				new AotChunkSizes( chunkSize, chunkSize, chunkSize, chunkSize, chunkSize, chunkSize, 500, 500 ) );
		List<AotTypeMetadata> types = new ArrayList<>();
		for ( Class<?> bean : List.of( AotBean.class, PackageBean.class ) ) {
			AotTypeMetadata type = new AotTypeMetadata( bean.getPackageName(), bean.getName(), bean.getName(),
					Modifier.isPublic( bean.getModifiers() ), false );
			Set<AotMemberMetadata> readers = new HashSet<>(), writers = new HashSet<>();
			Set<AotConstructorMetadata> constructors = new HashSet<>();
			for ( var field : bean.getDeclaredFields() ) {
				if ( Modifier.isStatic( field.getModifiers() ) )
					continue;
				AotFieldMetadata member = new AotFieldMetadata( field.getName(), Type.getDescriptor( field.getType() ),
						field.getType().isPrimitive(), bean.getName() );
				readers.add( member );
				if ( !Modifier.isFinal( field.getModifiers() ) )
					writers.add( member );
			}
			for ( var method : bean.getDeclaredMethods() ) {
				boolean getter = method.getParameterCount() == 0 && method.getReturnType() != void.class;
				boolean setter = method.getParameterCount() == 1;
				if ( !getter && !setter )
					continue;
				Class<?> value = getter ? method.getReturnType() : method.getParameterTypes()[0];
				AotMethodMetadata member = new AotMethodMetadata( method.getName(), Type.getMethodDescriptor( method ),
						value.isPrimitive(), bean.getName(), false, Type.getDescriptor( method.getReturnType() ) );
				( getter ? readers : writers ).add( member );
			}
			for ( var ctor : bean.getDeclaredConstructors() ) {
				List<AotParameterMetadata> parameters = new ArrayList<>();
				for ( Class<?> parameter : ctor.getParameterTypes() )
					parameters.add( new AotParameterMetadata( "arg",
							Type.getDescriptor( parameter ), parameter.isPrimitive() ) );
				constructors.add( new AotConstructorMetadata( bean.getName(), bean.getName(), Type.getConstructorDescriptor( ctor ), parameters ) );
			}
			ClassWriter writer = new ClassWriter( ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS );
			int index = types.size();
			new ClassReader( bytes( bean ) ).accept( generator.hostVisitor( writer, type, readers, writers, constructors, index ), 0 );
			write( hosts, bean.getName(), writer.toByteArray() );
			types.add( type );
		}
		// A few large hosts cover member chunking; the rest cover class/factory chunking cheaply.
		for ( int index = 0; index <= 1000; index++ ) {
			String name = "fixtures.Bean" + index;
			AotTypeMetadata type = new AotTypeMetadata( "fixtures", name, name, true, false );
			ClassWriter writer = new ClassWriter( ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS );
			writer.visit( GENERATED_CLASS_VERSION, Opcodes.ACC_PUBLIC, name.replace( '.', '/' ), null, "java/lang/Object", null );
			var ctor = writer.visitMethod( Opcodes.ACC_PUBLIC, "<init>", "()V", null, null );
			ctor.visitCode();
			ctor.visitVarInsn( Opcodes.ALOAD, 0 );
			ctor.visitMethodInsn( Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false );
			ctor.visitInsn( Opcodes.RETURN );
			ctor.visitMaxs( 0, 0 );
			ctor.visitEnd();
			Set<AotMemberMetadata> fields = new HashSet<>();
			int count = Set.of( 0, 499, 500, 999, 1000 ).contains( index ) ? 1001 : 1;
			for ( int member = 0; member < count; member++ ) {
				writer.visitField( Opcodes.ACC_PUBLIC, "f" + member, "J", null, null ).visitEnd();
				fields.add( new AotFieldMetadata( "f" + member, "J", true, name ) );
			}
			generator.emitHostMethods( writer, type, fields, fields, Set.of(), types.size() );
			writer.visitEnd();
			write( hosts, name, writer.toByteArray() );
			types.add( type );
		}
		// Constructor cases can contain many widening conversions, unlike a single field write.
		String constructorHost = "fixtures.Constructors";
		AotTypeMetadata constructorType = new AotTypeMetadata( "fixtures", constructorHost, constructorHost, true, false );
		ClassWriter constructorWriter = new ClassWriter( ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS );
		constructorWriter.visit( GENERATED_CLASS_VERSION, Opcodes.ACC_PUBLIC, "fixtures/Constructors", null, "java/lang/Object", null );
		Set<AotConstructorMetadata> overloads = new HashSet<>();
		for ( int arity = 0; arity <= 120; arity++ ) {
			String descriptor = "(" + "D".repeat( arity ) + ")V";
			var method = constructorWriter.visitMethod( Opcodes.ACC_PUBLIC, "<init>", descriptor, null, null );
			method.visitCode();
			method.visitVarInsn( Opcodes.ALOAD, 0 );
			method.visitMethodInsn( Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false );
			method.visitInsn( Opcodes.RETURN );
			method.visitMaxs( 0, 0 );
			method.visitEnd();
			List<AotParameterMetadata> parameters = new ArrayList<>();
			for ( int argument = 0; argument < arity; argument++ )
				parameters.add( new AotParameterMetadata( "arg", "D", true ) );
			overloads.add( new AotConstructorMetadata( constructorHost, constructorHost, descriptor, parameters ) );
		}
		generator.emitHostMethods( constructorWriter, constructorType, Set.of(), Set.of(), overloads, types.size() );
		constructorWriter.visitEnd();
		write( hosts, constructorHost, constructorWriter.toByteArray() );
		types.add( constructorType );
		for ( var entry : generator.generateClasses( types, false ).entrySet() ) {
			// Non-public hosts need their bridges in the same defining classloader.
			write( entry.getKey().endsWith( "$$HibernateAccessorBridge" ) ? hosts : application, entry.getKey(), entry.getValue() );
		}
		for ( Class<?> support : List.of( RuntimeHooks.class, RuntimeProbe.class, RuntimeProbe.Checked.class ) ) {
			write( hosts, support.getName(), bytes( support ) );
		}
		String core = Path.of( AccessorFactory.class.getProtectionDomain().getCodeSource().getLocation().toURI() ).toString();
		Path log = temporary.resolve( "runtime.log" );
		Process process = new ProcessBuilder( Path.of( System.getProperty( "java.home" ), "bin", "java" ).toString(),
				"-Xverify:all", "-cp", hosts + File.pathSeparator + core, RuntimeProbe.class.getName(), application.toString() )
				.redirectErrorStream( true ).redirectOutput( log.toFile() ).start();
		try {
			assertThat( process.waitFor( 60, TimeUnit.SECONDS ) ).as( "runtime process completed" ).isTrue();
			assertThat( process.exitValue() ).withFailMessage( Files.readString( log ) ).isZero();
		}
		finally {
			process.destroyForcibly();
		}
	}

	private static byte[] bytes(Class<?> type) throws Exception {
		try ( var stream = type.getResourceAsStream( "/" + type.getName().replace( '.', '/' ) + ".class" ) ) {
			return stream.readAllBytes();
		}
	}

	private static void write(Path root, String name, byte[] bytes) throws Exception {
		Path file = root.resolve( name.replace( '.', '/' ) + ".class" );
		Files.createDirectories( file.getParent() );
		Files.write( file, bytes );
	}
}
