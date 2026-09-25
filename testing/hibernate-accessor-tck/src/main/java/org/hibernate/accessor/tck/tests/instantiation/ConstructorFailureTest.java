/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.tck.tests.instantiation;

import org.hibernate.accessor.tck.util.TckHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ConstructorFailureTest {
	public static class FailingBean {
		public static final RuntimeException FAILURE = new IllegalStateException( "constructor failure" );

		public FailingBean() {
			throw FAILURE;
		}
	}

	@Test
	void constructorFailureRetainsTheOriginalCause() throws Exception {
		var instantiator = TckHelper.factory().instantiator( FailingBean.class.getConstructor() );
		Throwable thrown = assertThrows( RuntimeException.class, instantiator::create );

		while ( thrown.getCause() != null ) {
			thrown = thrown.getCause();
		}
		assertSame( FailingBean.FAILURE, thrown );
	}
}
