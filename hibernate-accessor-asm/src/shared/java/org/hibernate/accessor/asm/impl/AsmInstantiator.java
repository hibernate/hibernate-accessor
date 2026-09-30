/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.asm.impl;

import org.hibernate.accessor.AccessorException;
import org.hibernate.accessor.Instantiator;
import org.hibernate.accessor.asm.spi.AsmBulkAccessor;


// The bulk accessor retains its weakly cached index metadata.
record AsmInstantiator<T>(AsmBulkAccessor accessor, int index, int parameterCount) implements Instantiator<T> {

	@Override
	@SuppressWarnings("unchecked")
	public T create(Object... args) {
		if ( ( args == null ? 0 : args.length ) != parameterCount ) {
			throw new AccessorException( "Expected " + parameterCount + " constructor arguments" );
		}
		return (T) accessor.newInstance( index, args );
	}
}
