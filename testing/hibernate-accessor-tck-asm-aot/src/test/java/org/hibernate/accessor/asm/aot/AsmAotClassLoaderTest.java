package org.hibernate.accessor.asm.aot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Member;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.Instantiator;
import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.ValueWriter;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

class AsmAotClassLoaderTest implements Opcodes {
	@ParameterizedTest
	@ValueSource(ints = { 1, 256 })
	void checksIdentityForEveryLookupPath(int chunkSize) throws Exception {
		for ( boolean withFallback : new boolean[] { false, true } ) {
			Map<String, byte[]> classes = generate( chunkSize, withFallback );
			ClassLoader foreign = loader( classes );
			Map<String, byte[]> hosts = new LinkedHashMap<>( classes );
			byte[] factoryBytes = hosts.remove( "generated.Factory" );
			ClassLoader registered = loader( hosts );
			// The factory is deliberately in a child loader: delegation to a host's loader is valid.
			ClassLoader factoryLoader = loader( Map.of( "generated.Factory", factoryBytes ), registered );
			RuntimeHooks.loader = factoryLoader;
			List<Member> delegated = new ArrayList<>();
			ValueReader<?> reader = target -> "fallback";
			ValueWriter writer = (target, value) -> {};
			Instantiator<?> instantiator = arguments -> "fallback";
			AccessorFactory fallback = (AccessorFactory) Proxy.newProxyInstance(
					AccessorFactory.class.getClassLoader(), new Class<?>[] { AccessorFactory.class },
					(proxy, method, args) -> {
						delegated.add( (Member) args[0] );
						return switch ( method.getName() ) {
							case "valueReader" -> reader;
							case "valueWriter" -> writer;
							case "instantiator" -> instantiator;
							default -> throw new AssertionError( method );
						};
					} );
			Class<?> factoryType = factoryLoader.loadClass( "generated.Factory" );
			AccessorFactory factory = (AccessorFactory) ( withFallback
					? factoryType.getMethod( "create", AccessorFactory.class ).invoke( null, fallback )
					: factoryType.getMethod( "create" ).invoke( null ) );
			for ( String name : List.of( "AaAa", "AaBB", "BBAa", "BBBB" ) ) {
				Class<?> own = registered.loadClass( "identity." + name );
				Class<?> other = foreign.loadClass( own.getName() );
				assertThat( own ).isNotSameAs( other );
				assertThat( factory.valueReader( own.getDeclaredMethod( "get" ) ) ).isNotNull();
				assertThat( factory.valueWriter( own.getDeclaredMethod( "set", Object.class ) ) ).isNotNull();
				check( () -> factory.valueReader( other.getDeclaredMethod( "get" ) ), reader, withFallback );
				check( () -> factory.valueWriter( other.getDeclaredMethod( "set", Object.class ) ), writer, withFallback );
				if ( !own.isInterface() ) {
					Object target = factory.instantiator( own.getDeclaredConstructor() ).create();
					assertThat( target.getClass() ).isSameAs( own );
					factory.valueWriter( own.getDeclaredField( "value" ) ).set( target, "own" );
					assertThat( factory.valueReader( own.getDeclaredField( "value" ) ).get( target ) ).isEqualTo( "own" );
					check( () -> factory.valueReader( other.getDeclaredField( "value" ) ), reader, withFallback );
					check( () -> factory.valueWriter( other.getDeclaredField( "value" ) ), writer, withFallback );
					check( () -> factory.instantiator( other.getDeclaredConstructor() ), instantiator, withFallback );
				}
			}
			assertThat( delegated ).hasSize( withFallback ? 14 : 0 );
			assertThat( delegated ).allSatisfy( member -> assertThat( member.getDeclaringClass().getClassLoader() ).isSameAs( foreign ) );
		}
	}

	private static void check(Lookup lookup, Object fallback, boolean withFallback) throws Exception {
		if ( withFallback ) {
			assertThat( lookup.get() ).isSameAs( fallback );
		}
		else {
			assertThatThrownBy( lookup::get ).isInstanceOf( UnsupportedOperationException.class );
		}
	}

	@FunctionalInterface
	private interface Lookup {
		Object get() throws Exception;
	}

	private static ClassLoader loader(Map<String, byte[]> classes) {
		return loader( classes, AsmAotClassLoaderTest.class.getClassLoader() );
	}

	private static ClassLoader loader(Map<String, byte[]> classes, ClassLoader parent) {
		return new ClassLoader( parent ) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] bytes = classes.get( name );
				if ( bytes == null ) {
					throw new ClassNotFoundException( name );
				}
				return defineClass( name, bytes, 0, bytes.length );
			}
		};
	}

	private static Map<String, byte[]> generate(int chunkSize, boolean withFallback) {
		var names = new GenerationNames( "generated.Factory", "generated.Reader", "generated.Writer",
				"generated.Instantiator", RuntimeHooks.class.getName() );
		var generator = new AsmAotGenerator( names,
				new AotChunkSizes( 256, 256, 256, 256, 256, 256, chunkSize, 256 ) );
		Map<String, byte[]> classes = new LinkedHashMap<>();
		List<AotTypeMetadata> hosts = new ArrayList<>();
		// Equal hashes force chained chunk parts, including for public/non-public interfaces.
		for ( String simple : List.of( "AaAa", "AaBB", "BBAa", "BBBB" ) ) {
			boolean isInterface = hosts.size() >= 2;
			boolean isPublic = hosts.size() % 2 == 0;
			String name = "identity." + simple;
			String internal = name.replace( '.', '/' );
			var host = new AotTypeMetadata( "identity", name, name, isPublic, isInterface );
			ClassWriter cw = new ClassWriter( ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS );
			cw.visit( GeneratorConstants.GENERATED_CLASS_VERSION,
					( isPublic ? ACC_PUBLIC : 0 ) | ( isInterface ? ACC_INTERFACE | ACC_ABSTRACT : ACC_SUPER ),
					internal, null, "java/lang/Object", null );
			for ( String method : List.of( "get", "set" ) ) {
				String descriptor = method.equals( "get" ) ? "()Ljava/lang/Object;" : "(Ljava/lang/Object;)V";
				var mv = cw.visitMethod( ACC_PUBLIC, method, descriptor, null, null );
				mv.visitCode();
				if ( method.equals( "get" ) ) {
					mv.visitInsn( ACONST_NULL );
					mv.visitInsn( ARETURN );
				}
				else {
					mv.visitInsn( RETURN );
				}
				mv.visitMaxs( 0, 0 );
				mv.visitEnd();
			}
			var getter = new AotMethodMetadata( "get", "()Ljava/lang/Object;", false, name, isInterface, "Ljava/lang/Object;" );
			var setter = new AotMethodMetadata( "set", "(Ljava/lang/Object;)V", false, name, isInterface, "V" );
			Set<AotMemberMetadata> readers = new HashSet<>( Set.of( getter ) );
			Set<AotMemberMetadata> writers = new HashSet<>( Set.of( setter ) );
			Set<AotConstructorMetadata> constructors = Set.of();
			if ( !isInterface ) {
				cw.visitField( ACC_PRIVATE, "value", "Ljava/lang/Object;", null, null ).visitEnd();
				var field = new AotFieldMetadata( "value", "Ljava/lang/Object;", false, name );
				readers.add( field );
				writers.add( field );
				var mv = cw.visitMethod( ACC_PUBLIC, "<init>", "()V", null, null );
				mv.visitCode();
				mv.visitVarInsn( ALOAD, 0 );
				mv.visitMethodInsn( INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false );
				mv.visitInsn( RETURN );
				mv.visitMaxs( 0, 0 );
				mv.visitEnd();
				constructors = Set.of( new AotConstructorMetadata( name, name, "()V", List.of() ) );
			}
			generator.emitHostMethods( cw, host, readers, writers, constructors, hosts.size() );
			cw.visitEnd();
			classes.put( name, cw.toByteArray() );
			hosts.add( host );
		}
		classes.putAll( generator.generateClasses( hosts, withFallback ) );
		return classes;
	}
}
