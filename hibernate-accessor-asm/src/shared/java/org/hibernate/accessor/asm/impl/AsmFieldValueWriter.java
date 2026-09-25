/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.asm.impl;

import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.asm.spi.AsmBulkAccessor;

// Keep the weakly cached metadata alive for as long as an accessor uses it.
record AsmFieldValueWriter(AsmBulkAccessor accessor, Object cacheOwner, int index) implements ValueWriter {

	@Override
	public void set(Object instance, Object value) {
		accessor.writeByField( instance, index, value );
	}
}
