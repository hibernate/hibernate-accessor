/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.classfile.impl;

import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.classfile.spi.ClassFileBulkAccessor;

// Keep the weakly cached metadata alive for as long as an accessor uses it.
record ClassFileMethodValueReader<T>(ClassFileBulkAccessor accessor, Object cacheOwner, int index) implements ValueReader<T> {

	@Override
	@SuppressWarnings("unchecked")
	public T get(Object instance) {
		return (T) accessor.readByMethod( instance, index );
	}
}
