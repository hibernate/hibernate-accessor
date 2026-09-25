/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.lambda.tests;

import java.lang.invoke.MethodHandles;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import org.hibernate.accessor.AccessorFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LambdaLifetimeTest {
	public static class Bean {
		private String value;
		public String getValue() { return value; }
		public void setValue(String value) { this.value = value; }
	}

	@Test
	void repeatedFactoriesDoNotSpinUnboundedLambdaClasses() throws Exception {
		var getter = Bean.class.getMethod( "getValue" );
		var setter = Bean.class.getMethod( "setValue", String.class );
		Set<Class<?>> readers = new HashSet<>();
		Set<Class<?>> writers = new HashSet<>();
		for ( int i = 0; i < 128; i++ ) {
			var factory = AccessorFactory.lambda( MethodHandles.lookup() );
			readers.add( factory.valueReader( getter ).getClass() );
			writers.add( factory.valueWriter( setter ).getClass() );
		}
		assertEquals( 1, readers.size(), "lambda classes remain linked to the entity loader" );
		assertEquals( 1, writers.size(), "factory disposal must not cause new lambda classes" );
	}

	@Test
	void cachedAccessorsDoNotBypassLookupValidation() throws Exception {
		var getter = Bean.class.getMethod( "getValue" );
		var privileged = AccessorFactory.lambda( MethodHandles.lookup() ).valueReader( getter );
		var restricted = AccessorFactory.lambda( MethodHandles.publicLookup() ).valueReader( getter );
		// The existing reflection fallback may still access this method, but a reduced
		// lookup must not retrieve the privileged lambda from the shared cache.
		org.junit.jupiter.api.Assertions.assertNotSame( privileged.getClass(), restricted.getClass() );
	}

	@Test
	void hiddenEntityDoesNotBecomeStronglyLinkedThroughALambda() throws Exception {
		var reference = hiddenEntity();
		for ( int i = 0; i < 100 && reference.get() != null; i++ ) {
			System.gc();
			Thread.sleep( 25 );
		}
		org.junit.jupiter.api.Assertions.assertNull( reference.get() );
	}

	private static java.lang.ref.WeakReference<Class<?>> hiddenEntity() throws Exception {
		byte[] bytes;
		try ( var stream = Bean.class.getResourceAsStream( "LambdaLifetimeTest$Bean.class" ) ) {
			bytes = stream.readAllBytes();
		}
		var type = MethodHandles.lookup().defineHiddenClass( bytes, true, MethodHandles.Lookup.ClassOption.NESTMATE ).lookupClass();
		var factory = AccessorFactory.lambda( MethodHandles.lookup() );
		var reader = factory.valueReader( type.getMethod( "getValue" ) );
		var writer = factory.valueWriter( type.getMethod( "setValue", String.class ) );
		var bean = type.getConstructor().newInstance();
		writer.set( bean, "value" );
		assertEquals( "value", reader.get( bean ) );
		return new java.lang.ref.WeakReference<>( type );
	}

	@Test
	void concurrentFactoriesShareGeneratedClasses() {
		var classes = IntStream.range( 0, 64 ).parallel().mapToObj( i -> {
			try {
				return AccessorFactory.lambda( MethodHandles.lookup() )
						.valueReader( Bean.class.getMethod( "getValue" ) ).getClass();
			}
			catch (ReflectiveOperationException e) {
				throw new AssertionError( e );
			}
		} ).distinct().count();
		assertEquals( 1, classes );
	}
}
