/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.reflection.impl;

import java.lang.reflect.Field;
import java.util.Objects;

import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.logging.impl.CoreLog;

public class ReflectionFieldValueWriter implements ValueWriter {

	private final Field field;

	public ReflectionFieldValueWriter(Field field) {
		this.field = field;
		field.setAccessible( true );
	}

	@Override
	public void set(Object instance, Object value) {
		try {
			field.set( instance, value );
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
		ReflectionFieldValueWriter other = (ReflectionFieldValueWriter) obj;
		return field.equals( other.field );
	}
}
