/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.tck.tests.multivalue;

import org.hibernate.accessor.tck.tests.interfacemethod.GreetingService;
import org.hibernate.accessor.tck.tests.interfacemethod.GreetingServiceImpl;
import org.hibernate.accessor.tck.util.TckHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MultiValueEdgeCasesTest {

	public static class Bean {
		public String first;
		public String second;
		public int count;
		public final RuntimeException failure = new IllegalStateException( "failure" );

		public String readFailure() {
			throw failure;
		}

		public void writeFailure(String value) {
			throw failure;
		}
	}

	@Test
	void duplicateMembersRetainTheirPositionsAndWriteInOrder() throws Exception {
		var factory = TckHelper.factory();
		var first = Bean.class.getField( "first" );
		var second = Bean.class.getField( "second" );
		var bean = new Bean();
		var writer = factory.multiValueWriter( Bean.class, first, second, first );
		var reader = factory.multiValueReader( Bean.class, first, second, first );

		writer.set( bean, new Object[] { "initial", "middle", "last" } );

		assertEquals( "last", bean.first );
		assertEquals( "middle", bean.second );
		assertArrayEquals( new Object[] { "last", "middle", "last" }, reader.get( bean ) );
	}

	@Test
	void nullableReferencesAndPrimitivesRoundTripTogether() throws Exception {
		var factory = TckHelper.factory();
		var first = Bean.class.getField( "first" );
		var count = Bean.class.getField( "count" );
		var second = Bean.class.getField( "second" );
		var bean = new Bean();
		var writer = factory.multiValueWriter( Bean.class, first, count, second );
		var reader = factory.multiValueReader( Bean.class, first, count, second );

		writer.set( bean, new Object[] { null, 7, "present" } );
		assertArrayEquals( new Object[] { null, 7, "present" }, reader.get( bean ) );

		writer.set( bean, new Object[] { "present", 0, null } );
		assertArrayEquals( new Object[] { "present", 0, null }, reader.get( bean ) );
	}

	@Test
	void multiValueAccessAcceptsInterfaceDeclaredMethods() throws Exception {
		var factory = TckHelper.factory();
		var getter = GreetingService.class.getMethod( "getGreeting" );
		var setter = GreetingService.class.getMethod( "setGreeting", String.class );
		var bean = new GreetingServiceImpl();

		factory.multiValueWriter( GreetingServiceImpl.class, setter ).set( bean, new Object[] { "hello" } );

		assertArrayEquals( new Object[] { "hello" },
				factory.multiValueReader( GreetingServiceImpl.class, getter ).get( bean ) );
	}

	@Test
	void failureInLaterGetterPropagatesTheSameThrowable() throws Exception {
		var factory = TckHelper.factory();
		var bean = new Bean();
		bean.first = "before";
		var reader = factory.multiValueReader( Bean.class,
				Bean.class.getField( "first" ), Bean.class.getMethod( "readFailure" ) );

		assertSame( bean.failure, assertThrows( RuntimeException.class, () -> reader.get( bean ) ) );
	}

	@Test
	void failureInLaterSetterPropagatesTheSameThrowableAfterEarlierWrite() throws Exception {
		var factory = TckHelper.factory();
		var bean = new Bean();
		var writer = factory.multiValueWriter( Bean.class,
				Bean.class.getField( "first" ), Bean.class.getMethod( "writeFailure", String.class ) );

		assertSame( bean.failure, assertThrows( RuntimeException.class,
				() -> writer.set( bean, new Object[] { "written", "ignored" } ) ) );
		assertEquals( "written", bean.first );
	}
}
