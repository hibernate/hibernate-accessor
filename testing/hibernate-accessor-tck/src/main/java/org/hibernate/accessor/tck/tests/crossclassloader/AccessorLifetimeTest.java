/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.tck.tests.crossclassloader;

import java.lang.invoke.MethodHandles;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.spi.AccessorConfiguration;
import java.util.Map;
import org.hibernate.accessor.tck.tests.beans.LifecycleBean;
import org.hibernate.accessor.tck.util.IsolatingClassLoader;
import org.hibernate.accessor.tck.util.TckHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class AccessorLifetimeTest {
	@Test
	void retainedFactoryDoesNotRetainDisposableEntityLoaders() throws Exception {
		var factory = TckHelper.factory();
		List<WeakReference<?>> references = new ArrayList<>();
		for ( int i = 0; i < 8; i++ ) {
			references.addAll( disposableEntity( factory, false ) );
		}
		awaitCollection( references );
		Reference.reachabilityFence( factory );
	}

	@Test
	void completeApplicationCanBeCollected() throws Exception {
		awaitCollection( disposableEntity( TckHelper.factory(), true ) );
	}

	@Test
	void discardedFactoriesCanBeCollectedWithLiveEntityClass() throws Exception {
		List<WeakReference<?>> references = new ArrayList<>();
		for ( int i = 0; i < 8; i++ ) {
			addFactoryReference( references );
		}
		awaitCollection( references );
	}

	@Test
	void disposableImplementationLoaderDoesNotRemainAttachedToLiveEntityClass() throws Exception {
		if ( TckHelper.factory() == AccessorFactory.reflection() ) {
			return; // the reflection factory is deliberately a singleton
		}
		awaitCollection( disposableImplementation() );
	}

	private static List<WeakReference<?>> disposableImplementation() throws Exception {
		Class<?> implementation = TckHelper.factory().getClass();
		String prefix = implementation.getPackageName() + ".";
		ClassLoader loader = new ClassLoader( implementation.getClassLoader() ) {
			@Override
			protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
				if ( !name.startsWith( prefix ) ) {
					return super.loadClass( name, resolve );
				}
				synchronized ( getClassLoadingLock( name ) ) {
					Class<?> loaded = findLoadedClass( name );
					if ( loaded == null ) {
						try ( var stream = getParent().getResourceAsStream( name.replace( '.', '/' ) + ".class" ) ) {
							if ( stream == null ) { throw new ClassNotFoundException( name ); }
							byte[] bytes = stream.readAllBytes();
							loaded = defineClass( name, bytes, 0, bytes.length );
						}
						catch (java.io.IOException e) { throw new ClassNotFoundException( name, e ); }
					}
					if ( resolve ) { resolveClass( loaded ); }
					return loaded;
				}
			}
		};
		var factory = (AccessorFactory) loader.loadClass( implementation.getName() )
				.getConstructor( AccessorConfiguration.class ).newInstance( configuration( MethodHandles.lookup() ) );
		var accessors = exercise( factory, LifecycleBean.class );
		var references = new ArrayList<WeakReference<?>>();
		references.add( new WeakReference<>( loader ) );
		references.add( new WeakReference<>( factory ) );
		if ( implementation.getPackageName().contains( ".lambda." ) ) {
			// A reloaded implementation must not leave a new, permanently linked
			// lambda class behind in the surviving parent entity loader.
			references.add( new WeakReference<>( accessors[3].getClass() ) );
		}
		return references;
	}

	@Test
	void disposableMultiAccessorClassesUnloadWhileFactoryAndEntitySurvive() throws Exception {
		var factory = TckHelper.factory();
		List<WeakReference<?>> references = new ArrayList<>();
		for ( int i = 0; i < 16; i++ ) {
			addGeneratedClassReferences( factory, references );
		}
		awaitCollection( references );
		Reference.reachabilityFence( factory );
	}

	private static void addGeneratedClassReferences(AccessorFactory factory, List<WeakReference<?>> references) throws Exception {
		var field = LifecycleBean.class.getField( "value" );
		var reader = factory.multiValueReader( LifecycleBean.class, field );
		var writer = factory.multiValueWriter( LifecycleBean.class, field );
		if ( reader.getClass().isHidden() ) {
			references.add( new WeakReference<>( reader.getClass() ) );
			references.add( new WeakReference<>( writer.getClass() ) );
		}
	}

	@Test
	void concurrentFactoriesCanShareTheInjectedBridge() throws Exception {
		var loader = new IsolatingClassLoader( Set.of( LifecycleBean.class.getName() ), LifecycleBean.class.getClassLoader() );
		var type = loader.loadClass( LifecycleBean.class.getName() );
		var field = type.getField( "value" );
		var bean = type.getConstructor().newInstance();
		field.set( bean, "shared" );
		java.util.stream.IntStream.range( 0, 32 ).parallel().forEach( i -> {
			var reader = TckHelper.factory().multiValueReader( type, field );
			org.junit.jupiter.api.Assertions.assertArrayEquals( new Object[] { "shared" }, reader.get( bean ) );
		} );
	}

	@Test
	void publicAncestorBridgeIsNotReused() throws Exception {
		var factory = TckHelper.factory();
		String implementation = factory.getClass().getName();
		if ( !implementation.contains( ".asm." ) && !implementation.contains( ".bytebuddy." )
				&& !implementation.contains( ".classfile." ) ) {
			return;
		}
		String name = "org.hibernate.accessor.tck.tests.beans.shadowed.ShadowedBean";
		var loader = new IsolatingClassLoader( Set.of( name ), getClass().getClassLoader() );
		var type = loader.loadClass( name );
		var reader = factory.multiValueReader( type, type.getField( "value" ) );
		org.junit.jupiter.api.Assertions.assertTrue( reader.getClass().isHidden(), "must use the target loader's bridge" );
		org.junit.jupiter.api.Assertions.assertArrayEquals( new Object[] { "child" }, reader.get( type.getConstructor().newInstance() ) );
	}

	private static void addFactoryReference(List<WeakReference<?>> references) throws Exception {
		var factory = TckHelper.factory();
		exercise( factory, LifecycleBean.class );
		if ( factory != AccessorFactory.reflection() ) {
			references.add( new WeakReference<>( factory ) );
		}
	}

	private static List<WeakReference<?>> disposableEntity(AccessorFactory factory, boolean localLookup) throws Exception {
		var loader = new IsolatingClassLoader( Set.of( LifecycleBean.class.getName() ),
				LifecycleBean.class.getClassLoader() );
		var type = loader.loadClass( LifecycleBean.class.getName() );
		if ( localLookup && factory != AccessorFactory.reflection() ) {
			var lookup = (MethodHandles.Lookup) type.getMethod( "lookup" ).invoke( null );
			factory = factory.getClass().getConstructor( AccessorConfiguration.class ).newInstance( configuration( lookup ) );
		}
		var accessors = exercise( factory, type );
		var reference = new WeakReference<>( loader );
		// Positive control: live accessors must retain the type/loader they operate on.
		System.gc();
		assertNotNull( reference.get() );
		Reference.reachabilityFence( accessors );
		return List.of( reference, new WeakReference<>( type ) );
	}

	private static Object[] exercise(AccessorFactory factory, Class<?> type) throws Exception {
		var ctor = factory.instantiator( type.getConstructor() );
		var field = type.getField( "value" );
		var reader = factory.valueReader( field );
		var writer = factory.valueWriter( field );
		var methodReader = factory.valueReader( type.getMethod( "getValue" ) );
		var methodWriter = factory.valueWriter( type.getMethod( "setValue", String.class ) );
		var multiReader = factory.multiValueReader( type, field );
		var multiWriter = factory.multiValueWriter( type, field );
		var bean = ctor.create();
		writer.set( bean, "first" );
		methodWriter.set( bean, "second" );
		multiWriter.set( bean, new Object[] { "third" } );
		reader.get( bean );
		methodReader.get( bean );
		multiReader.get( bean );
		return new Object[] { ctor, reader, writer, methodReader, methodWriter, multiReader, multiWriter };
	}

	private static AccessorConfiguration configuration(MethodHandles.Lookup lookup) {
		return new AccessorConfiguration( lookup, Map.of( "hibernate.accessor.generation.strategy",
				System.getProperty( "hibernate.accessor.generation.strategy", "BULK_SWITCH" ) ) );
	}

	private static void awaitCollection(List<WeakReference<?>> references) throws Exception {
		for ( int attempt = 0; attempt < 100 && references.stream().anyMatch( r -> r.get() != null ); attempt++ ) {
			System.gc();
			Thread.sleep( 25 );
		}
		for ( var reference : references ) {
			assertNull( reference.get(), "still reachable after repeated full-GC requests" );
		}
	}
}
