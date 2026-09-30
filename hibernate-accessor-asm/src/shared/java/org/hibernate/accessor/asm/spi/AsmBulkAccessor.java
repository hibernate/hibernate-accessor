/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.asm.spi;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.hibernate.accessor.spi.BulkAccessorMetadata;

public interface AsmBulkAccessor {

	/** Index metadata retained by this instance, rather than by a class or cache entry. */
	default BulkAccessorMetadata metadata() {
		throw new UnsupportedOperationException( "This bulk accessor does not expose index metadata" );
	}

	default int fieldIndex(Field field) {
		return metadata().fieldIndex( field );
	}

	default int methodIndex(Method method) {
		return metadata().methodIndex( method );
	}

	default int constructorIndex(Constructor<?> constructor) {
		return metadata().constructorIndex( constructor );
	}

	Object readByField(Object instance, int index);

	void writeByField(Object instance, int index, Object value);

	Object readByMethod(Object instance, int index);

	void writeByMethod(Object instance, int index, Object value);

	Object newInstance(int index, Object[] args);
}
