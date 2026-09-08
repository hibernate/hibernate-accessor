/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.methodhandle.impl;

import java.lang.invoke.MethodHandle;

import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.internal.AccessorThrowables;

public class MethodHandleMethodValueWriter implements ValueWriter {
	private final MethodHandle target;

	public MethodHandleMethodValueWriter(MethodHandle target) {
		this.target = target;
	}

	@Override
	public void set(Object instance, Object value) {
		try {
			target.invoke( instance, value );
		}
		catch (Throwable t) {
			// Propagate whatever the setter body threw, unchanged, so every strategy behaves alike.
			throw AccessorThrowables.sneakyThrow( t );
		}
	}
}
