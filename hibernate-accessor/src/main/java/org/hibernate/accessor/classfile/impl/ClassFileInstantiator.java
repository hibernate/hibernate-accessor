package org.hibernate.accessor.classfile.impl;

import org.hibernate.accessor.AccessorException;
import org.hibernate.accessor.Instantiator;
import org.hibernate.accessor.classfile.spi.ClassFileBulkAccessor;

// The bulk accessor retains its weakly cached index metadata.
record ClassFileInstantiator<T>(ClassFileBulkAccessor accessor, int index, int parameterCount) implements Instantiator<T> {

	@Override
	@SuppressWarnings("unchecked")
	public T create(Object... args) {
		if ( ( args == null ? 0 : args.length ) != parameterCount ) {
			throw new AccessorException( "Expected " + parameterCount + " constructor arguments" );
		}
		return (T) accessor.newInstance( index, args );
	}
}
