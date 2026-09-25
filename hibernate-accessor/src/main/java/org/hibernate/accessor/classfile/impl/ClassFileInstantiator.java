/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.classfile.impl;

import org.hibernate.accessor.AccessorException;
import org.hibernate.accessor.Instantiator;
import org.hibernate.accessor.classfile.spi.ClassFileBulkAccessor;

// Keep the weakly cached metadata alive for as long as an accessor uses it.
record ClassFileInstantiator<T>(ClassFileBulkAccessor accessor, Object cacheOwner, int index, int parameterCount) implements Instantiator<T> {

	@Override
	@SuppressWarnings("unchecked")
	public T create(Object... args) {
		if ( ( args == null ? 0 : args.length ) != parameterCount ) {
			throw new AccessorException( "Expected " + parameterCount + " constructor arguments" );
		}
		return (T) accessor.newInstance( index, args );
	}
}
