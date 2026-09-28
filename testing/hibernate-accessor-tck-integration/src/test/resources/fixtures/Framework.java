package framework;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.InaccessibleObjectException;
import java.util.Objects;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.asm.AsmAccessorFactory;
import org.hibernate.accessor.bytebuddy.ByteBuddyAccessorFactory;

public class Framework {
	public static void main(String[] args) throws Exception {
		String strategy = args[0];
		String placement = args[1];
		boolean closed = Boolean.parseBoolean( args[2] );
		var lookup = MethodHandles.lookup();
		// Bootstrap before discovering any entity classes or their modules.
		AccessorFactory factory = switch ( strategy ) {
			case "reflection" -> AccessorFactory.reflection( lookup );
			case "methodhandle" -> AccessorFactory.methodHandle( lookup );
			case "lambda" -> AccessorFactory.lambda( lookup );
			case "asm" -> AsmAccessorFactory.factory( lookup );
			case "bytebuddy" -> ByteBuddyAccessorFactory.factory( lookup );
			case "classfile" -> AccessorFactory.classFile( lookup );
			default -> throw new AssertionError( strategy );
		};
		Class<?> entity = Class.forName( "entities.Entity" );
		Module framework = Framework.class.getModule();
		Module model = entity.getModule();
		boolean namedFramework = !placement.equals( "classpath" );
		boolean namedEntity = placement.equals( "modulepath" );
		check( framework.isNamed() == namedFramework, "framework placement" );
		check( model.isNamed() == namedEntity, "entity placement" );
		check( AccessorFactory.class.getModule().isNamed() == namedFramework, "accessor placement" );
		if ( namedFramework ) {
			check( !framework.canRead( model ), "framework must initially lack entity read edge" );
		}
		if ( namedEntity ) {
			check( !model.canRead( AccessorFactory.class.getModule() ), "entity must initially lack accessor read edge" );
			check( model.isOpen( "entities", framework ) == !closed, "qualified opening" );
			assertNotOpenToAccessors( model );
			// A factory without a framework lookup still has only library-module reflection access.
			expectDenied( () -> AccessorFactory.reflection().valueReader( entity.getDeclaredField( "value" ) ) );
		}
		if ( closed ) {
			expectDenied( () -> factory.valueReader( entity.getDeclaredField( "value" ) ) );
			expectDenied( () -> factory.valueWriter( entity.getDeclaredField( "value" ) ) );
			expectDenied( () -> factory.valueReader( entity.getDeclaredMethod( "getValue" ) ) );
			expectDenied( () -> factory.valueWriter( entity.getDeclaredMethod( "setValue", String.class ) ) );
			expectDenied( () -> factory.multiValueReader( entity, entity.getDeclaredField( "value" ) ) );
			expectDenied( () -> factory.multiValueWriter( entity, entity.getDeclaredField( "value" ) ) );
			expectDenied( () -> factory.valueWriter( entity.getDeclaredField( "finalValue" ) ) );
			expectDenied( () -> factory.instantiator( entity.getDeclaredConstructor() ) );
			System.out.println( "PASS: closed package rejected" );
			return;
		}

		// Start with the reflection fallback before any operation could establish a read edge.
		var finalWriter = factory.valueWriter( entity.getDeclaredField( "finalValue" ) );
		check( finalWriter.getClass().getName().contains( ".reflection." ), "final field must use reflection fallback" );
		Object instance = factory.instantiator( entity.getDeclaredConstructor() ).create();
		Object withArgument = factory.instantiator( entity.getDeclaredConstructor( String.class ) ).create( "argument" );
		var reader = factory.valueReader( entity.getDeclaredField( "value" ) );
		if ( !strategy.equals( "reflection" ) ) {
			check( !reader.getClass().getName().contains( ".reflection." ), "must exercise requested strategy" );
		}
		equal( "initial", reader.get( instance ) );
		equal( "argument", reader.get( withArgument ) );
		factory.valueWriter( entity.getDeclaredField( "value" ) ).set( instance, "field" );
		equal( "field", reader.get( instance ) );
		var methodReader = factory.valueReader( entity.getDeclaredMethod( "getValue" ) );
		factory.valueWriter( entity.getDeclaredMethod( "setValue", String.class ) ).set( instance, "method" );
		equal( "method", methodReader.get( instance ) );
		var bulkReader = factory.multiValueReader( entity, entity.getDeclaredField( "value" ) );
		var bulkWriter = factory.multiValueWriter( entity, entity.getDeclaredField( "value" ) );
		bulkWriter.set( instance, new Object[] { "bulk" } );
		equal( "bulk", bulkReader.get( instance )[0] );
		if ( strategy.equals( "asm" ) || strategy.equals( "bytebuddy" ) || strategy.equals( "classfile" ) ) {
			check( bulkReader.getClass().isHidden(), "must exercise generated accessor, not fallback" );
			check( bulkWriter.getClass().isHidden(), "must exercise generated writer, not fallback" );
		}
		// Read the constructor-initialized final value without mutating it.
		equal( "initial", factory.valueReader( entity.getDeclaredField( "finalValue" ) ).get( instance ) );
		if ( namedEntity ) {
			assertNotOpenToAccessors( model );
		}
		System.out.println( "PASS: " + strategy + " / " + placement );
	}

	private static void assertNotOpenToAccessors(Module model) {
		for ( Class<?> type : new Class<?>[] { AccessorFactory.class, AsmAccessorFactory.class, ByteBuddyAccessorFactory.class } ) {
			check( !model.isOpen( "entities", type.getModule() ), "entity package must not be opened to " + type.getModule() );
		}
	}

	private static void expectDenied(CheckedAction action) throws Exception {
		try {
			action.run();
		}
		catch (InaccessibleObjectException expected) {
			return;
		}
		throw new AssertionError( "closed package was accessible" );
	}

	private static void equal(Object expected, Object actual) {
		check( Objects.equals( expected, actual ), "expected " + expected + ", got " + actual );
	}

	private static void check(boolean condition, String message) {
		if ( !condition ) {
			throw new AssertionError( message );
		}
	}

	private interface CheckedAction {
		void run() throws Exception;
	}
}
