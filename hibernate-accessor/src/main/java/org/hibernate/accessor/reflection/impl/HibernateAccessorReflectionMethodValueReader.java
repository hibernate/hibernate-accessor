/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.reflection.impl;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

import org.hibernate.accessor.HibernateAccessorValueReader;
import org.hibernate.accessor.internal.HibernateAccessorThrowables;
import org.hibernate.accessor.logging.impl.CoreLog;

public class HibernateAccessorReflectionMethodValueReader<T> implements HibernateAccessorValueReader<T> {

	private final Method method;

	public HibernateAccessorReflectionMethodValueReader(Method getter) {
		this.method = getter;
		method.setAccessible( true );
	}

	@Override
	@SuppressWarnings("unchecked")
	public T get(Object instance) {
		try {
			return (T) method.invoke( instance );
		}
		catch (RuntimeException | IllegalAccessException e) {
			throw CoreLog.INSTANCE.errorInvokingMember( method, Objects.toString( instance ), e, e.getMessage() );
		}
		catch (InvocationTargetException e) {
			// Propagate whatever the getter body threw, unchanged, so every strategy behaves alike.
			Throwable thrown = e.getCause();
			if ( thrown == null ) {
				throw CoreLog.INSTANCE.errorInvokingMember( method, Objects.toString( instance ), e, e.getMessage() );
			}
			throw HibernateAccessorThrowables.sneakyThrow( thrown );
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + method + "]";
	}

	@Override
	public int hashCode() {
		return method.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if ( obj == null || !obj.getClass().equals( getClass() ) ) {
			return false;
		}
		HibernateAccessorReflectionMethodValueReader<?> other = (HibernateAccessorReflectionMethodValueReader<?>) obj;
		return method.equals( other.method );
	}
}
