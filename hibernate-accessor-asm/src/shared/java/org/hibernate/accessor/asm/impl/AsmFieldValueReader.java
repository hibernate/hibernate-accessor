package org.hibernate.accessor.asm.impl;

import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.asm.spi.AsmBulkAccessor;

// The bulk accessor retains its weakly cached index metadata.
record AsmFieldValueReader<T>(AsmBulkAccessor accessor, int index) implements ValueReader<T> {

	@Override
	@SuppressWarnings("unchecked")
	public T get(Object instance) {
		return (T) accessor.readByField( instance, index );
	}
}
