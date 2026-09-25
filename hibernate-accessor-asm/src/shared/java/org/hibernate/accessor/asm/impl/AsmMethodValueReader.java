/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.asm.impl;

import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.asm.spi.AsmBulkAccessor;

// Keep the weakly cached metadata alive for as long as an accessor uses it.
record AsmMethodValueReader<T>(AsmBulkAccessor accessor, Object cacheOwner, int index) implements ValueReader<T> {

	@Override
	@SuppressWarnings("unchecked")
	public T get(Object instance) {
		return (T) accessor.readByMethod( instance, index );
	}
}
