/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.asm.impl;

import java.lang.reflect.Method;

import org.hibernate.accessor.HibernateAccessorException;
import org.hibernate.accessor.HibernateAccessorValueWriter;
import org.hibernate.accessor.asm.spi.HibernateAccessorAsmBulkAccessor;

record HibernateAccessorAsmMethodValueWriter(HibernateAccessorAsmBulkAccessor accessor, int index, Method method) implements HibernateAccessorValueWriter {

	@Override
	public void set(Object instance, Object value) {
		try {
			accessor.writeByMethod( instance, index, value );
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
