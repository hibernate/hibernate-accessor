package org.hibernate.accessor.asm.aot;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.MultiValueAccessorGenerationException;

/** Runs with only core accessor APIs, transformed hosts and a framework-owned runtime bridge. */
public final class RuntimeProbe {
	public static void main(String[] args) throws Exception {
		for ( String forbidden : new String[] {
				"org.objectweb.asm.ClassReader",
				"net.bytebuddy.ByteBuddy",
				"org.hibernate.accessor.asm.aot.AsmAotGenerator",
				"org.hibernate.accessor.bytebuddy.aot.ByteBuddyAotGenerator" } ) {
			try {
				Class.forName( forbidden );
				throw new AssertionError( "Generator leaked: " + forbidden );
			}
			catch (ClassNotFoundException expected) {
			}
		}
		try ( URLClassLoader application = new URLClassLoader( new URL[] { Path.of( args[0] ).toUri().toURL() },
				RuntimeProbe.class.getClassLoader() ) ) {
			RuntimeHooks.loader = application;
			AccessorFactory factory = (AccessorFactory) application.loadClass( "generated.Factory" ).getMethod( "create" ).invoke( null );
			Class<?> bean = Class.forName( AotBean.class.getName() );
			var noArgs = factory.instantiator( bean.getDeclaredConstructor() );
			Object instance = noArgs.create();
			rejects( () -> noArgs.create( "extra" ) );
			var ctor = factory.instantiator( bean.getDeclaredConstructor( long.class ) );
			Object constructed = ctor.create( 42 );
			rejects( () -> ctor.create() );
			rejects( () -> ctor.create( 1L, 2L ) );
			var value = bean.getDeclaredField( "value" );
			equal( 42L, factory.valueReader( value ).get( constructed ) );
			for ( Object small : new Object[] { (byte) 2, (short) 2, 2, 2L, (char) 2 } ) {
				factory.valueWriter( value ).set( instance, small );
				equal( 2L, factory.valueReader( value ).get( instance ) );
				factory.valueWriter( bean.getDeclaredMethod( "setValue", long.class ) ).set( instance, small );
				equal( 2L, factory.valueReader( bean.getDeclaredMethod( "getValue" ) ).get( instance ) );
			}
			rejects( () -> factory.valueWriter( bean.getDeclaredField( "count" ) ).set( instance, 2L ) );
			rejects( () -> factory.valueWriter( value ).set( instance, null ) );
			// Field and method names overlap, and setter overloads must retain distinct dispatch indexes.
			factory.valueWriter( bean.getDeclaredMethod( "value", int.class ) ).set( instance, 3 );
			equal( 23L, factory.valueReader( value ).get( instance ) );
			factory.valueWriter( bean.getDeclaredMethod( "value", long.class ) ).set( instance, 3L );
			equal( 13L, factory.valueReader( value ).get( instance ) );
			factory.valueWriter( value ).set( instance, 5L );
			equal( 5L, factory.valueReader( value ).get( instance ) );
			equal( 105L, factory.valueReader( bean.getDeclaredMethod( "value" ) ).get( instance ) );
			for ( String name : new String[] { "Ea", "FB" } ) {
				var field = bean.getDeclaredField( name );
				factory.valueWriter( field ).set( instance, name );
				equal( name, factory.valueReader( field ).get( instance ) );
			}
			try {
				factory.valueReader( bean.getDeclaredMethod( "fail" ) ).get( instance );
				throw new AssertionError( "Expected user exception" );
			}
			catch (RuntimeException e) {
				if ( e != AotBean.FAILURE )
					throw new AssertionError( "Exception was wrapped", e );
			}
			try {
				factory.multiValueReader( bean, value );
				throw new AssertionError( "Expected bulk rejection" );
			}
			catch (MultiValueAccessorGenerationException expected) {
			}
			try {
				factory.multiValueWriter( bean, value );
				throw new AssertionError( "Expected bulk rejection" );
			}
			catch (MultiValueAccessorGenerationException expected) {
			}
			Class<?> hidden = Class.forName( PackageBean.class.getName() );
			Object other = factory.instantiator( hidden.getDeclaredConstructor() ).create();
			var privateField = hidden.getDeclaredField( "value" );
			factory.valueWriter( privateField ).set( other, "bridge" );
			equal( "bridge", factory.valueReader( privateField ).get( other ) );
			Class<?> constructorHost = Class.forName( "fixtures.Constructors" );
			for ( int arity : new int[] { 0, 1, 2, 119, 120 } ) {
				Class<?>[] parameters = new Class<?>[arity];
				Object[] arguments = new Object[arity];
				Arrays.fill( parameters, double.class );
				Arrays.fill( arguments, 7 );
				Object created = factory.instantiator( constructorHost.getConstructor( parameters ) ).create( arguments );
				equal( constructorHost, created.getClass() );
			}
			// Exercise class dispatch on both sides of the 1000-host boundary and field/string chunks.
			for ( int index : new int[] { 0, 499, 500, 999, 1000 } ) {
				Class<?> large = Class.forName( "fixtures.Bean" + index );
				Object target = large.getConstructor().newInstance();
				for ( int member : new int[] { 0, 499, 500, 999, 1000 } ) {
					var field = large.getField( "f" + member );
					factory.valueWriter( field ).set( target, member );
					equal( (long) member, factory.valueReader( field ).get( target ) );
				}
			}
		}
	}

	private static void equal(Object expected, Object actual) {
		if ( !expected.equals( actual ) )
			throw new AssertionError( expected + " != " + actual );
	}

	private static void rejects(Checked action) throws Exception {
		try {
			action.run();
		}
		catch (RuntimeException expected) {
			return;
		}
		throw new AssertionError( "Expected rejection" );
	}

	interface Checked { void run() throws Exception; }
}
