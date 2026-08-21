/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.internal;

/**
 * Internal utilities for propagating throwables raised by user-supplied getters and setters.
 *
 * <p>This package is intentionally not exported: it is an implementation detail shared by the
 * reflection, method-handle and lambda accessor strategies.
 */
public final class HibernateAccessorThrowables {

	private HibernateAccessorThrowables() {
	}

	/**
	 * Rethrows the given throwable unchanged, without wrapping it and without requiring the caller
	 * to declare it. This preserves the exact throwable instance raised by a user getter/setter body
	 * -- be it an {@link Error}, a {@link RuntimeException} or a checked exception -- so that
	 * every accessor strategy propagates it identically (the bytecode-based strategies do this for free,
	 * since the JVM does not enforce checked-exception declarations).
	 *
	 * <p>The declared {@link RuntimeException} return type lets callers write {@code throw sneakyThrow(t);}
	 * so the compiler knows control flow does not continue; the method never actually returns.
	 *
	 * @param t the throwable to rethrow (must not be {@code null})
	 * @param <E> the (unchecked, from the compiler's point of view) type the throwable is cast to
	 * @return never returns normally
	 * @throws E always, carrying {@code t} unchanged
	 */
	@SuppressWarnings("unchecked")
	public static <E extends Throwable> RuntimeException sneakyThrow(Throwable t) throws E {
		throw (E) t;
	}
}
