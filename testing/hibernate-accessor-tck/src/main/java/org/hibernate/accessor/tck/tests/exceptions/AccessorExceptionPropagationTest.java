/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.tck.tests.exceptions;

import org.hibernate.accessor.AccessorException;
import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.tck.tests.beans.ThrowingBean;
import org.hibernate.accessor.tck.util.TckHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that an exception thrown by a getter or setter body propagates unchanged -- the exact
 * same throwable instance, neither wrapped nor swapped for a {@link AccessorException} --
 * and that this holds identically for runtime and checked exceptions across every accessor strategy.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Exceptions thrown by getters/setters propagate unchanged")
public class AccessorExceptionPropagationTest {

	private AccessorFactory factory;

	@BeforeAll
	void setup() {
		factory = TckHelper.factory();
	}

	@Test
	@DisplayName("Getter throwing a RuntimeException propagates the exact instance")
	void getterRuntimeException() throws Exception {
		Method getter = ThrowingBean.class.getDeclaredMethod( "getRuntimeThrowing" );
		ValueReader<?> reader = factory.valueReader( getter );

		RuntimeException thrown = assertThrows(
				RuntimeException.class,
				() -> reader.get( new ThrowingBean() )
		);
		assertSame( ThrowingBean.GETTER_RUNTIME_FAILURE, thrown );
	}

	@Test
	@DisplayName("Setter throwing a RuntimeException propagates the exact instance")
	void setterRuntimeException() throws Exception {
		Method setter = ThrowingBean.class.getDeclaredMethod( "setRuntimeThrowing", String.class );
		ValueWriter writer = factory.valueWriter( setter );

		RuntimeException thrown = assertThrows(
				RuntimeException.class,
				() -> writer.set( new ThrowingBean(), "x" )
		);
		assertSame( ThrowingBean.SETTER_RUNTIME_FAILURE, thrown );
	}

	@Test
	@DisplayName("Getter throwing a checked exception propagates the exact instance (sneaky-thrown)")
	void getterCheckedException() throws Exception {
		Method getter = ThrowingBean.class.getDeclaredMethod( "getCheckedThrowing" );
		ValueReader<?> reader = factory.valueReader( getter );

		ThrowingBean.CheckedFailure thrown = assertThrows(
				ThrowingBean.CheckedFailure.class,
				() -> reader.get( new ThrowingBean() )
		);
		assertSame( ThrowingBean.GETTER_CHECKED_FAILURE, thrown );
	}

	@Test
	@DisplayName("Setter throwing a checked exception propagates the exact instance (sneaky-thrown)")
	void setterCheckedException() throws Exception {
		Method setter = ThrowingBean.class.getDeclaredMethod( "setCheckedThrowing", String.class );
		ValueWriter writer = factory.valueWriter( setter );

		ThrowingBean.CheckedFailure thrown = assertThrows(
				ThrowingBean.CheckedFailure.class,
				() -> writer.set( new ThrowingBean(), "x" )
		);
		assertSame( ThrowingBean.SETTER_CHECKED_FAILURE, thrown );
	}
}
