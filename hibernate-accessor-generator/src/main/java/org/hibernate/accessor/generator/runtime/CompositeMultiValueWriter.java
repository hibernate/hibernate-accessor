/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.generator.runtime;

import org.hibernate.accessor.HibernateAccessorMultiValueWriter;
import org.hibernate.accessor.HibernateAccessorValueWriter;

public class CompositeMultiValueWriter implements HibernateAccessorMultiValueWriter {

	private final HibernateAccessorValueWriter[] writers;

	public CompositeMultiValueWriter(HibernateAccessorValueWriter[] writers) {
		this.writers = writers;
	}

	@Override
	public void set(Object instance, Object[] values) {
		for ( int i = 0; i < writers.length; i++ ) {
			writers[i].set( instance, values[i] );
		}
	}
}
