/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.reflection.impl;

import java.lang.reflect.Field;
import java.util.Objects;

import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.logging.impl.CoreLog;
import org.hibernate.accessor.spi.AccessContext;

public class ReflectionFieldValueReader<T> implements ValueReader<T> {

	private final Field field;

	ReflectionFieldValueReader(Field field, AccessContext access) {
		this.field = field;
		access.makeAccessible( field );
	}

	@Override
	@SuppressWarnings("unchecked")
	public T get(Object instance) {
		try {
			return (T) field.get( instance );
		}
		catch (RuntimeException | IllegalAccessException e) {
			throw CoreLog.INSTANCE.errorInvokingMember( field, Objects.toString( instance ), e,
					e.getMessage() );
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + field + "]";
	}

	@Override
	public int hashCode() {
		return field.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if ( obj == null || !obj.getClass().equals( getClass() ) ) {
			return false;
		}
		ReflectionFieldValueReader<?> other = (ReflectionFieldValueReader<?>) obj;
		return field.equals( other.field );
	}
}
