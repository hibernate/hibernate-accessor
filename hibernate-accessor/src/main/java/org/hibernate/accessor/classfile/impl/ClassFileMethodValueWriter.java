/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.classfile.impl;

import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.classfile.spi.ClassFileBulkAccessor;

record ClassFileMethodValueWriter(ClassFileBulkAccessor accessor, int index) implements ValueWriter {

	@Override
	public void set(Object instance, Object value) {
		accessor.writeByMethod( instance, index, value );
	}
}
