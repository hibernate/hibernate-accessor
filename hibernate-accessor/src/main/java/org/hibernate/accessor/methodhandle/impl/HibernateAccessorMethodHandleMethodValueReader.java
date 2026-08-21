/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.methodhandle.impl;

import java.lang.invoke.MethodHandle;

import org.hibernate.accessor.HibernateAccessorValueReader;
import org.hibernate.accessor.internal.HibernateAccessorThrowables;

public class HibernateAccessorMethodHandleMethodValueReader<T> implements HibernateAccessorValueReader<T> {
	private final MethodHandle target;

	public HibernateAccessorMethodHandleMethodValueReader(MethodHandle target) {
		this.target = target;
	}

	@Override
	@SuppressWarnings("unchecked")
	public T get(Object instance) {
		try {
			return (T) target.invoke( instance );
		}
		catch (Throwable t) {
			// Propagate whatever the getter body threw, unchanged, so every strategy behaves alike.
			throw HibernateAccessorThrowables.sneakyThrow( t );
		}
	}
}
