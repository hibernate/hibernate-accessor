package org.hibernate.accessor.asm.impl;

import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.asm.spi.AsmBulkAccessor;

// The bulk accessor retains its weakly cached index metadata.
record AsmFieldValueWriter(AsmBulkAccessor accessor, int index) implements ValueWriter {

	@Override
	public void set(Object instance, Object value) {
		accessor.writeByField( instance, index, value );
	}
}
