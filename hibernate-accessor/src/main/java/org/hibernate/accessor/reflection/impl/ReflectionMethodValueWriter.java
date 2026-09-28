/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.reflection.impl;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.internal.AccessorThrowables;
import org.hibernate.accessor.logging.impl.CoreLog;
import org.hibernate.accessor.spi.LookupAccess;

public class ReflectionMethodValueWriter implements ValueWriter {

	private final Method method;

	ReflectionMethodValueWriter(Method setter, LookupAccess access) {
		this.method = setter;
		access.makeAccessible( setter );
	}

	@Override
	public void set(Object instance, Object value) {
		try {
			method.invoke( instance, value );
		}
		catch (RuntimeException | IllegalAccessException e) {
			throw CoreLog.INSTANCE.errorInvokingMember( method, Objects.toString( instance ), e, e.getMessage() );
		}
		catch (InvocationTargetException e) {
			// Propagate whatever the setter body threw, unchanged, so every strategy behaves alike.
			Throwable thrown = e.getCause();
			if ( thrown == null ) {
				throw CoreLog.INSTANCE.errorInvokingMember( method, Objects.toString( instance ), e, e.getMessage() );
			}
			throw AccessorThrowables.sneakyThrow( thrown );
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
		ReflectionMethodValueWriter other = (ReflectionMethodValueWriter) obj;
		return Objects.equals( method, other.method );
	}
}
