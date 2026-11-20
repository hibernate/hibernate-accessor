package org.hibernate.accessor.classfile.impl;

import org.hibernate.accessor.Instantiator;
import org.hibernate.accessor.classfile.spi.ClassFileBulkAccessor;
import org.hibernate.accessor.spi.MemberValidation;

// The bulk accessor retains its weakly cached index metadata.
record ClassFileInstantiator<T>(ClassFileBulkAccessor accessor, int index, int parameterCount) implements Instantiator<T> {

	@Override
	@SuppressWarnings("unchecked")
	public T create(Object... args) {
		MemberValidation.validateConstructorArity( args, parameterCount );
		return (T) accessor.newInstance( index, args );
	}
}
