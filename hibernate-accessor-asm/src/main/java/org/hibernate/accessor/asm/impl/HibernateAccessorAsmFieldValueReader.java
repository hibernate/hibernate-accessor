/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.asm.impl;

import org.hibernate.accessor.HibernateAccessorValueReader;
import org.hibernate.accessor.asm.spi.HibernateAccessorAsmBulkAccessor;

record HibernateAccessorAsmFieldValueReader<T>(HibernateAccessorAsmBulkAccessor accessor, int index) implements HibernateAccessorValueReader<T> {

	@Override
	@SuppressWarnings("unchecked")
	public T get(Object instance) {
		return (T) accessor.readByField(instance, index);
	}
}
