/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.classfile.impl;

import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.classfile.spi.ClassFileBulkAccessor;

// Keep the weakly cached metadata alive for as long as an accessor uses it.
record ClassFileFieldValueWriter(ClassFileBulkAccessor accessor, Object cacheOwner, int index) implements ValueWriter {

	@Override
	public void set(Object instance, Object value) {
		accessor.writeByField( instance, index, value );
	}
}
