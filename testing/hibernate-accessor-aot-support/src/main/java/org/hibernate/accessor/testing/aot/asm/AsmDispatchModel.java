package org.hibernate.accessor.testing.aot.asm;

import static org.hibernate.accessor.asm.aot.GeneratorConstants.GENERATED_CLASS_VERSION;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.Instantiator;
import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.asm.aot.AotChunkSizes;
import org.hibernate.accessor.asm.aot.AotConstructorMetadata;
import org.hibernate.accessor.asm.aot.AotFieldMetadata;
import org.hibernate.accessor.asm.aot.AotMemberMetadata;
import org.hibernate.accessor.asm.aot.AotParameterMetadata;
import org.hibernate.accessor.asm.aot.AotTypeMetadata;
import org.hibernate.accessor.asm.aot.AsmAotGenerator;
import org.hibernate.accessor.asm.aot.GenerationNames;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/** Internal generated workload shared by boundary tests and dispatch benchmarks. */
public final class AsmDispatchModel implements Opcodes {
	public final Map<String, byte[]> bytecode = new LinkedHashMap<>();
	public final AccessorFactory factory;
	public final ValueReader<?>[] readers;
	public final ValueWriter[] writers;
	public final Instantiator<?>[] constructors;
	public final Object[][] arguments;
	public final Object[] targets;
	public final Field[] fields;
	public final Constructor<?>[] reflectedConstructors;
	public final ClassLoader loader;

	public AsmDispatchModel(int types, int members, int constructorCount, AotChunkSizes chunks, boolean collisions) throws Exception {
		if ( types <= 0 || members <= 0 || constructorCount <= 0 || constructorCount > 64 ) {
			throw new IllegalArgumentException( "Positive counts required; constructor count must be <= 64" );
		}
		var names = new GenerationNames( "dispatch.Factory", "dispatch.Reader", "dispatch.Writer",
				"dispatch.Instantiator", "dispatch.Hooks" );
		var generator = new AsmAotGenerator( names, chunks );
		List<AotTypeMetadata> hosts = new ArrayList<>();
		for ( int t = 0; t < types; t++ ) {
			String name = "dispatch.model." + name( t, collisions );
			var host = new AotTypeMetadata( "dispatch.model", name, name, true, false );
			String internal = name.replace( '.', '/' );
			ClassWriter cw = new ClassWriter( ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS );
			cw.visit( GENERATED_CLASS_VERSION, ACC_PUBLIC | ACC_SUPER, internal, null, "java/lang/Object", null );
			Set<AotMemberMetadata> properties = new TreeSet<>();
			for ( int m = 0; m < members; m++ ) {
				String field = name( m, collisions );
				cw.visitField( ACC_PUBLIC, field, "Ljava/lang/Object;", null, null ).visitEnd();
				properties.add( new AotFieldMetadata( field, "Ljava/lang/Object;", false, name ) );
			}
			Set<AotConstructorMetadata> ctors = new TreeSet<>();
			for ( int c = 0; c < constructorCount; c++ ) {
				String descriptor = "(" + "Ljava/lang/Object;".repeat( c ) + ")V";
				var mv = cw.visitMethod( ACC_PUBLIC, "<init>", descriptor, null, null );
				mv.visitCode();
				mv.visitVarInsn( ALOAD, 0 );
				mv.visitMethodInsn( INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false );
				mv.visitVarInsn( ALOAD, 0 );
				if ( c == 0 )
					mv.visitInsn( ACONST_NULL );
				else
					mv.visitVarInsn( ALOAD, c );
				mv.visitFieldInsn( PUTFIELD, internal, name( 0, collisions ), "Ljava/lang/Object;" );
				mv.visitInsn( RETURN );
				mv.visitMaxs( 0, 0 );
				mv.visitEnd();
				List<AotParameterMetadata> parameters = new ArrayList<>();
				for ( int a = 0; a < c; a++ )
					parameters.add( new AotParameterMetadata( "p" + a, "Ljava/lang/Object;", false ) );
				ctors.add( new AotConstructorMetadata( name, name, descriptor, parameters ) );
			}
			generator.emitHostMethods( cw, host, properties, properties, ctors, t );
			cw.visitEnd();
			bytecode.put( name, cw.toByteArray() );
			hosts.add( host );
		}
		bytecode.putAll( generator.generateClasses( hosts, false ) );
		ClassWriter hooks = new ClassWriter( ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS );
		hooks.visit( GENERATED_CLASS_VERSION, ACC_PUBLIC, "dispatch/Hooks", null, "java/lang/Object", null );
		for ( String role : List.of( "Reader", "Writer", "Instantiator" ) ) {
			var mv = hooks.visitMethod( ACC_PUBLIC | ACC_STATIC, "create" + role, "(II)Ljava/lang/Object;", null, null );
			mv.visitCode();
			mv.visitTypeInsn( NEW, "dispatch/" + role );
			mv.visitInsn( DUP );
			mv.visitVarInsn( ILOAD, 0 );
			mv.visitVarInsn( ILOAD, 1 );
			mv.visitMethodInsn( INVOKESPECIAL, "dispatch/" + role, "<init>", "(II)V", false );
			mv.visitInsn( ARETURN );
			mv.visitMaxs( 0, 0 );
			mv.visitEnd();
		}
		hooks.visitEnd();
		bytecode.put( "dispatch.Hooks", hooks.toByteArray() );
		loader = new ClassLoader( getClass().getClassLoader() ) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] bytes = bytecode.get( name );
				if ( bytes == null )
					throw new ClassNotFoundException( name );
				return defineClass( name, bytes, 0, bytes.length );
			}
		};
		factory = (AccessorFactory) loader.loadClass( names.factory() ).getMethod( "create" ).invoke( null );
		int total = Math.multiplyExact( types, members );
		readers = new ValueReader<?>[total];
		writers = new ValueWriter[total];
		targets = new Object[total];
		fields = new Field[total];
		constructors = new Instantiator<?>[Math.multiplyExact( types, constructorCount )];
		reflectedConstructors = new Constructor<?>[constructors.length];
		arguments = new Object[constructors.length][];
		for ( int t = 0; t < types; t++ ) {
			Class<?> type = loader.loadClass( hosts.get( t ).host() );
			Object target = type.getConstructor().newInstance();
			for ( int m = 0; m < members; m++ ) {
				int i = t * members + m;
				fields[i] = type.getField( name( m, collisions ) );
				readers[i] = factory.valueReader( fields[i] );
				writers[i] = factory.valueWriter( fields[i] );
				targets[i] = target;
				Object expected = i;
				writers[i].set( target, expected );
				if ( !expected.equals( readers[i].get( target ) ) || !expected.equals( fields[i].get( target ) ) ) {
					throw new AssertionError( "Incorrect dispatch at " + i );
				}
			}
			for ( int c = 0; c < constructorCount; c++ ) {
				int i = t * constructorCount + c;
				Class<?>[] parameters = new Class<?>[c];
				Arrays.fill( parameters, Object.class );
				reflectedConstructors[i] = type.getConstructor( parameters );
				constructors[i] = factory.instantiator( reflectedConstructors[i] );
				arguments[i] = new Object[c];
				Arrays.fill( arguments[i], "argument" );
				Object instance = constructors[i].create( arguments[i] );
				Object value = type.getField( name( 0, collisions ) ).get( instance );
				if ( instance.getClass() != type || !Objects.equals( value, c == 0 ? null : "argument" ) ) {
					throw new AssertionError( "Incorrect constructor dispatch at " + i );
				}
			}
		}
	}

	private static String name(int index, boolean collisions) {
		if ( !collisions )
			return "N" + String.format( Locale.ROOT, "%08d", index );
		StringBuilder name = new StringBuilder();
		for ( int bit = 30; bit >= 0; bit-- )
			name.append( ( index & ( 1 << bit ) ) == 0 ? "Aa" : "BB" );
		return name.toString();
	}
}
