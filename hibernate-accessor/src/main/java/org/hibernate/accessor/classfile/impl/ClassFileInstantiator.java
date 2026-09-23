/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.classfile.impl;

import org.hibernate.accessor.Instantiator;
import org.hibernate.accessor.classfile.spi.ClassFileBulkAccessor;

record ClassFileInstantiator<T>(ClassFileBulkAccessor accessor, int index) implements Instantiator<T> {

	@Override
	@SuppressWarnings("unchecked")
	public T create(Object... args) {
		return (T) accessor.newInstance( index, args );
	}
}
