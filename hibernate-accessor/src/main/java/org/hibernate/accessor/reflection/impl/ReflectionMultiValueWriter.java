/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.reflection.impl;

import org.hibernate.accessor.MultiValueWriter;
import org.hibernate.accessor.ValueWriter;

public class ReflectionMultiValueWriter implements MultiValueWriter {

	private final ValueWriter[] writers;

	public ReflectionMultiValueWriter(ValueWriter[] writers) {
		this.writers = writers;
	}

	@Override
	public void set(Object instance, Object[] values) {
		for ( int i = 0; i < writers.length; i++ ) {
			writers[i].set( instance, values[i] );
		}
	}
}
