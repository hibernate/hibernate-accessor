/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.lambda.impl;

import java.lang.invoke.MethodHandle;

import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.internal.AccessorThrowables;

public class LambdaFieldValueWriter implements ValueWriter {
	private final MethodHandle setter;

	public LambdaFieldValueWriter(MethodHandle setter) {
		this.setter = setter;
	}

	@Override
	public void set(Object instance, Object value) {
		try {
			setter.invoke( instance, value );
		}
		catch (Throwable t) {
			// Propagate whatever the setter body threw, unchanged, so every strategy behaves alike.
			throw AccessorThrowables.sneakyThrow( t );
		}
	}
}
