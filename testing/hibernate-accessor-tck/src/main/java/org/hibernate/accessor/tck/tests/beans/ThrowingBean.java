package org.hibernate.accessor.tck.tests.beans;

/**
 * Bean whose getters and setters deliberately throw, used to verify that every accessor strategy
 * propagates the exact throwable raised by the accessor body -- unchanged and unwrapped --
 * regardless of whether it is a {@link RuntimeException} or a checked exception.
 *
 * <p>The thrown instances are stored as {@code static final} sentinels so tests can assert the very
 * same object propagates out (not merely one of the same type).
 */
public class ThrowingBean {

	public static final RuntimeException GETTER_RUNTIME_FAILURE =
			new IllegalStateException( "getter runtime failure" );
	public static final RuntimeException SETTER_RUNTIME_FAILURE =
			new IllegalStateException( "setter runtime failure" );
	public static final CheckedFailure GETTER_CHECKED_FAILURE =
			new CheckedFailure( "getter checked failure" );
	public static final CheckedFailure SETTER_CHECKED_FAILURE =
			new CheckedFailure( "setter checked failure" );

	public String getRuntimeThrowing() {
		throw GETTER_RUNTIME_FAILURE;
	}

	public void setRuntimeThrowing(String value) {
		throw SETTER_RUNTIME_FAILURE;
	}

	public String getCheckedThrowing() throws CheckedFailure {
		throw GETTER_CHECKED_FAILURE;
	}

	public void setCheckedThrowing(String value) throws CheckedFailure {
		throw SETTER_CHECKED_FAILURE;
	}

	/** A genuinely checked exception (extends {@link Exception}, not {@link RuntimeException}). */
	public static final class CheckedFailure extends Exception {
		public CheckedFailure(String message) {
			super( message );
		}
	}
}
