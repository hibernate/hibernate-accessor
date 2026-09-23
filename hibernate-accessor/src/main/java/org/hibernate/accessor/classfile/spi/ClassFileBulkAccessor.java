/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.classfile.spi;

public interface ClassFileBulkAccessor {

	Object readByField(Object instance, int index);

	void writeByField(Object instance, int index, Object value);

	Object readByMethod(Object instance, int index);

	void writeByMethod(Object instance, int index, Object value);

	Object newInstance(int index, Object[] args);
}
