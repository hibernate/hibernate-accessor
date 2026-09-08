/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.asm.impl;

import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.asm.spi.AsmBulkAccessor;

record AsmMethodValueWriter(AsmBulkAccessor accessor, int index) implements ValueWriter {

	@Override
	public void set(Object instance, Object value) {
		accessor.writeByMethod( instance, index, value );
	}
}
