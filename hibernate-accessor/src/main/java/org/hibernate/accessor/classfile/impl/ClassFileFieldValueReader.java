/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.classfile.impl;

import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.classfile.spi.ClassFileBulkAccessor;

record ClassFileFieldValueReader<T>(ClassFileBulkAccessor accessor, int index) implements ValueReader<T> {

	@Override
	@SuppressWarnings("unchecked")
	public T get(Object instance) {
		return (T) accessor.readByField( instance, index );
	}
}
