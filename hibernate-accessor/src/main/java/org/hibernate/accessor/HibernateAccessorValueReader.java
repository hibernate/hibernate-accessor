/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor;

/**
 * Reads a value from a field or getter method on an object instance.
 *
 * <p>Obtain an instance via {@link HibernateAccessorFactory#valueReader(java.lang.reflect.Field)}
 * or {@link HibernateAccessorFactory#valueReader(java.lang.reflect.Method)}.
 *
 * @param <T> the type of the value being read
 */
public interface HibernateAccessorValueReader<T> {

	/**
	 * Reads the value from the given object instance.
	 *
	 * <p>Any exception thrown by the underlying getter method -- whether an {@link Error},
	 * a {@link RuntimeException} or a checked exception -- propagates unchanged, as the exact
	 * throwable the getter raised. This behaviour is identical across all accessor strategies. Note
	 * that a checked exception may therefore be thrown even though it is not declared here.
	 *
	 * @param instance the object to read from
	 * @return the value read from the instance
	 * @throws HibernateAccessorException if the accessor infrastructure itself fails (as opposed to
	 * the getter body throwing, which propagates unchanged)
	 */
	T get(Object instance);
}
