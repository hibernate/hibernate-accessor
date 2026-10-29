package org.hibernate.accessor.tck.tests.instantiation;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hibernate.accessor.tck.util.TckHelper;

import org.junit.jupiter.api.Test;

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
