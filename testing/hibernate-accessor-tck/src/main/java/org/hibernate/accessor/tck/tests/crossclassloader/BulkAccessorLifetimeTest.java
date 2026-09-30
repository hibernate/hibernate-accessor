/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.tck.tests.crossclassloader;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.Instantiator;
import org.hibernate.accessor.MultiValueReader;
import org.hibernate.accessor.MultiValueWriter;
import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.spi.BulkAccessorMetadata;
import org.hibernate.accessor.tck.tests.beans.LifecycleBean;
import org.hibernate.accessor.tck.tests.beans.inheritance.ChildBean;
import org.hibernate.accessor.tck.tests.beans.inheritance.ParentBean;
import org.hibernate.accessor.tck.util.TckHelper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class BulkAccessorLifetimeTest {

	@Test
	void eachReturnedAccessorRetainsBulkAndMetadataUntilReleased() throws Exception {
		for ( int kind = 0; kind < 7; kind++ ) {
			var factory = TckHelper.factory();
			String implementation = factory.getClass().getName();
			assumeTrue( implementation.contains( "BulkSwitchAccessorFactory" )
					|| implementation.contains( "ClassFileAccessorFactory" ) );
			var references = retainAndExercise( factory, kind );
			for ( int attempt = 0; attempt < 100 && references.stream().anyMatch( r -> r.get() != null ); attempt++ ) {
				System.gc();
				Thread.sleep( 25 );
			}
			for ( var reference : references ) {
				assertNull( reference.get(), "a surviving factory/entity must not retain bulk metadata" );
			}
			Reference.reachabilityFence( factory );
		}
	}

	private static List<WeakReference<?>> retainAndExercise(AccessorFactory factory, int kind) throws Exception {
		var field = LifecycleBean.class.getField( "value" );
		var parentField = ParentBean.class.getDeclaredField( "parentField" );
		var childField = ChildBean.class.getDeclaredField( "childField" );
		Object returned = switch ( kind ) {
			case 0 -> factory.valueReader( field );
			case 1 -> factory.valueWriter( field );
			case 2 -> factory.valueReader( LifecycleBean.class.getMethod( "getValue" ) );
			case 3 -> factory.valueWriter( LifecycleBean.class.getMethod( "setValue", String.class ) );
			case 4 -> factory.instantiator( LifecycleBean.class.getConstructor() );
			case 5 -> factory.multiValueReader( ChildBean.class, parentField, childField );
			case 6 -> factory.multiValueWriter( ChildBean.class, parentField, childField );
			default -> throw new AssertionError( kind );
		};
		Class<?>[] types = kind < 5 ? new Class<?>[] { LifecycleBean.class }
				: new Class<?>[] { ParentBean.class, ChildBean.class };
		var references = new ArrayList<WeakReference<?>>();
		for ( Class<?> type : types ) {
			addReferences( factory, type, references );
		}
		System.gc();
		for ( var reference : references ) {
			assertNotNull( reference.get(), "each live returned accessor must retain bulk and metadata" );
		}
		for ( int i = 0; i < types.length; i++ ) {
			assertSame( references.get( 2 * i ).get(), cachedBulk( factory, types[i] ), "live bulk must remain cached" );
		}
		var bean = new LifecycleBean();
		bean.value = "retained";
		if ( returned instanceof ValueReader<?> reader ) {
			assertEquals( "retained", reader.get( bean ) );
		}
		else if ( returned instanceof ValueWriter writer ) {
			writer.set( bean, "written" );
			assertEquals( "written", bean.value );
		}
		else if ( returned instanceof Instantiator<?> instantiator ) {
			assertEquals( LifecycleBean.class, instantiator.create().getClass() );
		}
		else if ( returned instanceof MultiValueReader reader ) {
			var child = new ChildBean();
			child.setParentField( "parent" );
			child.setChildField( "child" );
			org.junit.jupiter.api.Assertions.assertArrayEquals( new Object[] { "parent", "child" }, reader.get( child ) );
		}
		else if ( returned instanceof MultiValueWriter writer ) {
			var child = new ChildBean();
			writer.set( child, new Object[] { "parent", "child" } );
			assertEquals( "parent", child.getParentField() );
			assertEquals( "child", child.getChildField() );
		}
		Reference.reachabilityFence( returned );
		return references;
	}

	private static void addReferences(AccessorFactory factory, Class<?> type, List<WeakReference<?>> references) throws Exception {
		Object bulk = cachedBulk( factory, type );
		assertNotNull( bulk, "must exercise generated bulk accessors rather than reflection fallback" );
		BulkAccessorMetadata metadata = (BulkAccessorMetadata) bulk.getClass().getMethod( "metadata" ).invoke( bulk );
		references.add( new WeakReference<>( bulk ) );
		references.add( new WeakReference<>( metadata ) );
	}

	private static Object cachedBulk(AccessorFactory factory, Class<?> type) throws Exception {
		Class<?> implementation = factory.getClass();
		while ( implementation != Object.class ) {
			try {
				var field = implementation.getDeclaredField( "cache" );
				field.setAccessible( true );
				var cache = (ClassValue<?>) field.get( factory );
				var slot = (AtomicReference<?>) cache.get( type );
				return ( (WeakReference<?>) slot.get() ).get();
			}
			catch (NoSuchFieldException e) {
				implementation = implementation.getSuperclass();
			}
		}
		throw new AssertionError( "No bulk cache found" );
	}
}
