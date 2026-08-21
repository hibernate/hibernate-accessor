/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor;

/**
 * Writes a value to a field or setter method on an object instance.
 *
 * <p>Obtain an instance via {@link HibernateAccessorFactory#valueWriter(java.lang.reflect.Field)}
 * or {@link HibernateAccessorFactory#valueWriter(java.lang.reflect.Method)}.
 */
public interface HibernateAccessorValueWriter {

	/**
	 * Sets the value on the given object instance.
	 *
	 * <p>Any exception thrown by the underlying setter method -- whether an {@link Error},
	 * a {@link RuntimeException} or a checked exception -- propagates unchanged, as the exact
	 * throwable the setter raised. This behaviour is identical across all accessor strategies. Note
	 * that a checked exception may therefore be thrown even though it is not declared here.
	 *
	 * @param instance the object to write to
	 * @param value the value to set
	 * @throws HibernateAccessorException if the accessor infrastructure itself fails (as opposed to
	 * the setter body throwing, which propagates unchanged)
	 */
	void set(Object instance, Object value);
}
