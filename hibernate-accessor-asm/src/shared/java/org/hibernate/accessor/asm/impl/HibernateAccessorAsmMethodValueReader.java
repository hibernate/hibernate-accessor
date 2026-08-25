/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.asm.impl;

import java.lang.reflect.Method;

import org.hibernate.accessor.HibernateAccessorException;
import org.hibernate.accessor.HibernateAccessorValueReader;
import org.hibernate.accessor.asm.spi.HibernateAccessorAsmBulkAccessor;

record HibernateAccessorAsmMethodValueReader<T>(HibernateAccessorAsmBulkAccessor accessor, int index, Method method) implements HibernateAccessorValueReader<T> {

	@Override
	@SuppressWarnings("unchecked")
	public T get(Object instance) {
		try {
			return (T) accessor.readByMethod( instance, index );
		}
		catch (HibernateAccessorException e) {
			throw e;
		}
		catch (RuntimeException e) {
			throw new HibernateAccessorException(
					"Exception while invoking '" + method + "' on '" + instance + "': " + e.getMessage(), e );
		}
	}
}
