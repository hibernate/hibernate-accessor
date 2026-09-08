/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.asm.impl;

import org.hibernate.accessor.Instantiator;
import org.hibernate.accessor.asm.spi.AsmBulkAccessor;


record AsmInstantiator<T>(AsmBulkAccessor accessor, int index) implements Instantiator<T> {

	@Override
	@SuppressWarnings("unchecked")
	public T create(Object... args) {
		return (T) accessor.newInstance( index, args );
	}
}
