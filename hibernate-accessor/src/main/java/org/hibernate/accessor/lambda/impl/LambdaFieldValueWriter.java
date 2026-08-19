/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.lambda.impl;

import org.hibernate.accessor.HibernateAccessorValueWriter;
import org.hibernate.accessor.logging.impl.CoreLog;

import java.lang.invoke.MethodHandle;

public class LambdaFieldValueWriter implements HibernateAccessorValueWriter {
	private final MethodHandle setter;

	public LambdaFieldValueWriter(MethodHandle setter) {
		this.setter = setter;
	}

	@Override
	public void set(Object instance, Object value) {
		try {
			setter.invoke( instance, value );
		}
		catch (Throwable t) {
			if ( t instanceof Error ) {
				throw (Error) t;
			}
			throw CoreLog.INSTANCE.errorInvokingHandle( setter, String.valueOf( instance ), t, t.getMessage() );
		}
	}
}
