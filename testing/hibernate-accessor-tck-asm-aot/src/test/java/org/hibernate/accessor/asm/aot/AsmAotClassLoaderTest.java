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
			Map<String, byte[]> application = new LinkedHashMap<>();
			for ( String role : List.of( "Factory", "Reader", "Writer", "Instantiator" ) ) {
				String name = "generated." + role;
				application.put( name, hosts.remove( name ) );
			}
			ClassLoader registered = loader( hosts );
			// The factory is deliberately in a child loader: delegation to a host's loader is valid.
			ClassLoader factoryLoader = loader( application, registered );
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

	@ParameterizedTest
	@ValueSource(ints = { 1, 256 })
	void independentApplicationsAndRenumberedHosts(int chunkSize) throws Exception {
		Map<String, byte[]> original = generate( chunkSize, false );
		Map<String, byte[]> hostBytes = new LinkedHashMap<>( original );
		for ( String role : List.of( "Factory", "Reader", "Writer", "Instantiator" ) ) {
			hostBytes.remove( "generated." + role );
		}
		ClassWriter earlier = new ClassWriter( ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS );
		earlier.visit( GeneratorConstants.GENERATED_CLASS_VERSION, ACC_PUBLIC, "identity/A000", null, "java/lang/Object", null );
		new AsmAotGenerator( new GenerationNames( "generated.Factory", "generated.Reader", "generated.Writer", "generated.Instantiator" ) )
				.emitHostMethods( earlier, new AotTypeMetadata( "identity", "identity.A000", "identity.A000", true, false ),
						Set.of(), Set.of(), Set.of() );
		earlier.visitEnd();
		hostBytes.put( "identity.A000", earlier.toByteArray() );
		ClassLoader parent = loader( hostBytes );
		List<AotTypeMetadata> metadata = new ArrayList<>();
		for ( String name : List.of( "AaAa", "AaBB", "BBAa", "BBBB" ) ) {
			int index = metadata.size();
			metadata.add( new AotTypeMetadata( "identity", "identity." + name, "identity." + name,
					index % 2 == 0, index >= 2 ) );
		}
		List<AccessorFactory> factories = new ArrayList<>();
		List<ClassLoader> children = new ArrayList<>();
		ClassLoader previous = null;
		for ( int application = 0; application < 3; application++ ) {
			if ( application == 2 ) {
				// Reuse the transformed parent hosts, but shift every application class index.
				metadata.add( 0, new AotTypeMetadata( "identity", "identity.A000", "identity.A000", true, false ) );
			}
			var generator = new AsmAotGenerator( new GenerationNames( "generated.Factory", "generated.Reader",
					"generated.Writer", "generated.Instantiator" ),
					new AotChunkSizes( 1, 1, 1, 1, 1, 1, chunkSize, 1 ) );
			Map<String, byte[]> output = generator.generateClasses( metadata, false );
			output.keySet().removeIf( hostBytes::containsKey );
			ClassLoader child = loader( output, parent );
			assertThat( child ).isNotSameAs( previous );
			previous = child;
			AccessorFactory factory = (AccessorFactory) child.loadClass( "generated.Factory" ).getMethod( "create" ).invoke( null );
			factories.add( factory );
			children.add( child );
			for ( String name : List.of( "AaAa", "AaBB" ) ) {
				Class<?> host = parent.loadClass( "identity." + name );
				Object target = factory.instantiator( host.getDeclaredConstructor() ).create();
				var reader = factory.valueReader( host.getDeclaredField( "value" ) );
				assertThat( reader.getClass().getClassLoader() ).isSameAs( child );
				factory.valueWriter( host.getDeclaredField( "value" ) ).set( target, "application" + application );
				assertThat( reader.get( target ) ).isEqualTo( "application" + application );
				assertThat( factory.valueReader( host.getDeclaredMethod( "get" ) ).get( target ) ).isNull();
				factory.valueWriter( host.getDeclaredMethod( "set", Object.class ) ).set( target, "ignored" );
				assertThat( host.getMethod( GeneratorConstants.METHOD_NAME_FIELD_READER_INDEX, String.class )
						.getReturnType() ).isSameAs( int.class );
			}
		}
		// All applications remain usable after the others have initialized their factories.
		Class<?> host = parent.loadClass( "identity.AaAa" );
		for ( int i = 0; i < factories.size(); i++ ) {
			AccessorFactory factory = factories.get( i );
			var instantiator = factory.instantiator( host.getDeclaredConstructor() );
			var reader = factory.valueReader( host.getDeclaredField( "value" ) );
			var writer = factory.valueWriter( host.getDeclaredField( "value" ) );
			assertThat( instantiator.getClass().getClassLoader() ).isSameAs( children.get( i ) );
			assertThat( reader.getClass().getClassLoader() ).isSameAs( children.get( i ) );
			assertThat( writer.getClass().getClassLoader() ).isSameAs( children.get( i ) );
			Object target = instantiator.create();
			writer.set( target, i );
			assertThat( reader.get( target ) ).isEqualTo( i );
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
				"generated.Instantiator" );
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
			generator.emitHostMethods( cw, host, readers, writers, constructors );
			cw.visitEnd();
			classes.put( name, cw.toByteArray() );
			hosts.add( host );
		}
		classes.putAll( generator.generateClasses( hosts, withFallback ) );
		return classes;
	}
}
