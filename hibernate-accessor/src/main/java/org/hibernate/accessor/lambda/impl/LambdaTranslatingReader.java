/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.lambda.impl;

import org.hibernate.accessor.HibernateAccessorException;
import org.hibernate.accessor.HibernateAccessorValueReader;

/**
 * Functional interface whose {@link #getRaw} SAM is implemented directly by a
 * {@code LambdaMetafactory}-generated getter call, while the {@code default get}
 * translates any exception raised by that getter into a {@link HibernateAccessorException}.
 *
 * <p>The metafactory object <em>is</em> the reader (no wrapper object): {@code get} calls
 * {@code getRaw} on the same instance, which the JIT inlines, so exception translation adds
 * no meaningful per-call cost over a bare metafactory lambda.
 */
public interface LambdaTranslatingReader<T> extends HibernateAccessorValueReader<T> {

	/** Implemented by {@code LambdaMetafactory}; calls the target getter directly. */
	T getRaw(Object instance);

	@Override
	default T get(Object instance) {
		try {
			return getRaw( instance );
		}
		catch (HibernateAccessorException e) {
			throw e;
		}
		catch (RuntimeException e) {
			throw new HibernateAccessorException(
					"Exception while reading value from '" + instance + "': " + e.getMessage(), e );
		}
	}
}
