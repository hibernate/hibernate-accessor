/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.lambda.impl;

import org.hibernate.accessor.HibernateAccessorValueReader;
import org.hibernate.accessor.logging.impl.CoreLog;

import java.lang.invoke.MethodHandle;

public class LambdaFieldValueReader<T> implements HibernateAccessorValueReader<T> {
	private final MethodHandle getter;

	@SuppressWarnings("unchecked")
	public LambdaFieldValueReader(MethodHandle getter) {
		this.getter = getter;
	}

	@Override
	@SuppressWarnings("unchecked")
	public T get(Object instance) {
		try {
			return (T) getter.invoke( instance );
		}
		catch (Throwable t) {
			if ( t instanceof Error ) {
				throw (Error) t;
			}
			throw CoreLog.INSTANCE.errorInvokingHandle( getter, String.valueOf( instance ), t, t.getMessage() );
		}
	}
}
