/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.methodhandle.impl;

import java.lang.invoke.MethodHandle;

import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.internal.AccessorThrowables;

public class MethodHandleMethodValueReader<T> implements ValueReader<T> {
	private final MethodHandle target;

	public MethodHandleMethodValueReader(MethodHandle target) {
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
			throw AccessorThrowables.sneakyThrow( t );
		}
	}
}
