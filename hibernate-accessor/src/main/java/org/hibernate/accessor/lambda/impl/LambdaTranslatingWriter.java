/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.lambda.impl;

import org.hibernate.accessor.HibernateAccessorException;
import org.hibernate.accessor.HibernateAccessorValueWriter;

/**
 * Functional interface whose {@link #setRaw} SAM is implemented directly by a
 * {@code LambdaMetafactory}-generated setter call, while the {@code default set}
 * translates any exception raised by that setter into a {@link HibernateAccessorException}.
 *
 * <p>See {@link LambdaTranslatingReader} for why this adds no meaningful per-call cost.
 */
public interface LambdaTranslatingWriter extends HibernateAccessorValueWriter {

	/** Implemented by {@code LambdaMetafactory}; calls the target setter directly. */
	void setRaw(Object instance, Object value);

	@Override
	default void set(Object instance, Object value) {
		try {
			setRaw( instance, value );
		}
		catch (HibernateAccessorException e) {
			throw e;
		}
		catch (RuntimeException e) {
			throw new HibernateAccessorException(
					"Exception while writing value to '" + instance + "': " + e.getMessage(), e );
		}
	}
}
