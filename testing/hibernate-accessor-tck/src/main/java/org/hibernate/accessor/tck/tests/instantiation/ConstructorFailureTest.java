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

	public static class CheckedFailingBean {
		public static final Exception FAILURE = new Exception( "checked constructor failure" );

		public CheckedFailingBean() throws Exception {
			throw FAILURE;
		}
	}

	public static class ErrorFailingBean {
		public static final Error FAILURE = new AssertionError( "constructor error" );

		public ErrorFailingBean() {
			throw FAILURE;
		}
	}

	@Test
	void checkedConstructorFailurePropagatesUnchanged() throws Exception {
		var instantiator = TckHelper.factory().instantiator( CheckedFailingBean.class.getConstructor() );
		Throwable thrown = assertThrows( Exception.class, instantiator::create );
		assertSame( CheckedFailingBean.FAILURE, thrown );
	}

	@Test
	void runtimeConstructorFailurePropagatesUnchanged() throws Exception {
		var instantiator = TckHelper.factory().instantiator( FailingBean.class.getConstructor() );
		Throwable thrown = assertThrows( RuntimeException.class, instantiator::create );
		assertSame( FailingBean.FAILURE, thrown );
	}

	@Test
	void constructorErrorPropagatesUnchanged() throws Exception {
		var instantiator = TckHelper.factory().instantiator( ErrorFailingBean.class.getConstructor() );
		assertSame( ErrorFailingBean.FAILURE, assertThrows( Error.class, instantiator::create ) );
	}
}
