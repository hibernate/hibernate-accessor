package org.hibernate.accessor.asm.aot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.GENERATED_CLASS_VERSION;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.accessor.AccessorFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

class AsmAotCollisionTest {
	private static final int COUNT = 4096;
	private static final String FACTORY = "generated.Factory";
	private static final GenerationNames NAMES = new GenerationNames( FACTORY, "generated.Reader", "generated.Writer",
			"generated.Instantiator", RuntimeHooks.class.getName() );

	@Test
	void collidingMemberNamesKeepDispatchIndexesAndRejectMissingKeys() throws Exception {
		List<String> fields = new ArrayList<>();
		for ( int i = 0; i < COUNT; i++ )
			fields.add( collisionName( i ) );
		assertThat( fields.stream().map( String::hashCode ).distinct() ).hasSize( 1 );
		assertThat( collisionName( COUNT ).hashCode() ).isEqualTo( fields.get( 0 ).hashCode() );
		AsmAotGenerator generator = new AsmAotGenerator( NAMES );
		AotTypeMetadata host = type( "generated.CollidingMembers" );
		Map<String, byte[]> output = new HashMap<>( generator.generateClasses( List.of( host ), false ) );
		output.put( host.host(), model( generator, host, fields, 0 ) );
		ClassLoader loader = loader( output );
		RuntimeHooks.loader = loader;
		try {
			AccessorFactory factory = (AccessorFactory) loader.loadClass( FACTORY ).getMethod( "create" ).invoke( null );
			Class<?> bean = loader.loadClass( host.host() );
			Object instance = bean.getConstructor().newInstance();
			for ( int i : new int[] { 0, 499, 500, 999, 1000, COUNT - 1 } ) {
				var field = bean.getField( fields.get( i ) );
				factory.valueWriter( field ).set( instance, i );
				assertThat( factory.valueReader( field ).get( instance ) ).isEqualTo( i );
				assertThat( field.getInt( instance ) ).isEqualTo( i );
			}
			assertThat( bean.getMethod( "$$__hibernateAccessor_fieldReader", String.class )
					.invoke( null, collisionName( COUNT ) ) ).isNull();
			assertThat( bean.getMethod( "$$__hibernateAccessor_fieldWriter", String.class )
					.invoke( null, collisionName( COUNT ) ) ).isNull();
		}
		finally {
			RuntimeHooks.loader = null;
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void collidingClassNamesKeepDispatchAndFallback(boolean fallback) throws Exception {
		AsmAotGenerator generator = new AsmAotGenerator( NAMES );
		List<AotTypeMetadata> hosts = new ArrayList<>();
		Map<String, byte[]> output = new HashMap<>();
		for ( int i = COUNT - 1; i >= 0; i-- ) {
			AotTypeMetadata host = type( "generated." + collisionName( i ) );
			output.put( host.host(), model( generator, host, List.of( "value" ), hosts.size() ) );
			hosts.add( host );
		}
		output.putAll( generator.generateClasses( hosts, fallback ) );
		AotTypeMetadata missing = type( "generated." + collisionName( COUNT ) );
		output.put( missing.host(), model( null, missing, List.of( "value" ), 0 ) );
		ClassLoader loader = loader( output );
		RuntimeHooks.loader = loader;
		try {
			Class<?> factoryClass = loader.loadClass( FACTORY );
			AccessorFactory factory = (AccessorFactory) ( fallback
					? factoryClass.getMethod( "create", AccessorFactory.class ).invoke( null, AccessorFactory.reflection( MethodHandles.lookup() ) )
					: factoryClass.getMethod( "create" ).invoke( null ) );
			for ( int i : new int[] { 0, 499, 500, 999, 1000, COUNT - 1 } ) {
				Class<?> bean = loader.loadClass( "generated." + collisionName( i ) );
				Object instance = factory.instantiator( bean.getConstructor() ).create();
				var field = bean.getField( "value" );
				factory.valueWriter( field ).set( instance, i );
				assertThat( factory.valueReader( field ).get( instance ) ).isEqualTo( i );
				assertThat( field.getInt( instance ) ).isEqualTo( i );
			}
			Class<?> bean = loader.loadClass( missing.host() );
			var field = bean.getField( "value" );
			if ( fallback ) {
				Object instance = factory.instantiator( bean.getConstructor() ).create();
				factory.valueWriter( field ).set( instance, 42 );
				assertThat( factory.valueReader( field ).get( instance ) ).isEqualTo( 42 );
			}
			else {
				assertThatThrownBy( () -> factory.instantiator( bean.getConstructor() ) ).isInstanceOf( UnsupportedOperationException.class );
				assertThatThrownBy( () -> factory.valueReader( field ) ).isInstanceOf( UnsupportedOperationException.class );
				assertThatThrownBy( () -> factory.valueWriter( field ) ).isInstanceOf( UnsupportedOperationException.class );
			}
		}
		finally {
			RuntimeHooks.loader = null;
		}
	}

	private static AotTypeMetadata type(String name) {
		return new AotTypeMetadata( "generated", name, name, true, false );
	}

	private static String collisionName(int number) {
		StringBuilder result = new StringBuilder();
		for ( int bit = 12; bit >= 0; bit-- )
			result.append( ( number & ( 1 << bit ) ) == 0 ? "Aa" : "BB" );
		return result.toString();
	}

	private static byte[] model(AsmAotGenerator generator, AotTypeMetadata host, List<String> fields, int index) {
		ClassWriter writer = new ClassWriter( ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS );
		writer.visit( GENERATED_CLASS_VERSION, Opcodes.ACC_PUBLIC, host.host().replace( '.', '/' ), null, "java/lang/Object", null );
		var ctor = writer.visitMethod( Opcodes.ACC_PUBLIC, "<init>", "()V", null, null );
		ctor.visitCode();
		ctor.visitVarInsn( Opcodes.ALOAD, 0 );
		ctor.visitMethodInsn( Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false );
		ctor.visitInsn( Opcodes.RETURN );
		ctor.visitMaxs( 0, 0 );
		ctor.visitEnd();
		Set<AotMemberMetadata> members = new HashSet<>();
		for ( String field : fields ) {
			writer.visitField( Opcodes.ACC_PUBLIC, field, "I", null, null ).visitEnd();
			members.add( new AotFieldMetadata( field, "I", true, host.host() ) );
		}
		if ( generator != null )
			generator.emitHostMethods( writer, host, members, members,
					Set.of( new AotConstructorMetadata( host.host(), host.host(), "()V", List.of() ) ), index );
		writer.visitEnd();
		return writer.toByteArray();
	}

	private static ClassLoader loader(Map<String, byte[]> output) {
		return new ClassLoader( AsmAotCollisionTest.class.getClassLoader() ) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] bytes = output.get( name );
				if ( bytes == null )
					throw new ClassNotFoundException( name );
				return defineClass( name, bytes, 0, bytes.length );
			}
		};
	}
}
