/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.classfile.impl;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.hibernate.accessor.AccessorException;
import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.spi.AccessorConfiguration;

/**
 * Lazy holder that resolves the ClassFile accessor factory implementation via
 * {@link MethodHandle} to avoid a compile-time dependency
 * on the classfile source set.
 */
public final class ClassFileFactoryBootstrap {
	static final MethodHandle CONSTRUCTOR;

	static {
		try {
			MethodHandles.Lookup lookup = MethodHandles.lookup();
			Class<?> implClass = lookup.findClass( "org.hibernate.accessor.classfile.impl.ClassFileAccessorFactory" );
			CONSTRUCTOR = lookup.findConstructor(
					implClass,
					MethodType.methodType( void.class, AccessorConfiguration.class )
			);
		}
		catch (ClassNotFoundException e) {
			throw new AccessorException(
					"ClassFile accessor factory not available. On JDK 17-23, add the SmallRye "
							+ "jdk-classfile-backport library to the classpath. On JDK 24+, the "
							+ "implementation is built-in.",
					e
			);
		}
		catch (NoSuchMethodException | IllegalAccessException e) {
			throw new AccessorException( "Failed to resolve ClassFile accessor factory constructor", e );
		}
	}

	public static AccessorFactory instance(AccessorConfiguration configuration) {
		try {
			return (AccessorFactory) ClassFileFactoryBootstrap.CONSTRUCTOR.invoke( configuration );
		}
		catch (AccessorException e) {
			throw e;
		}
		catch (Throwable t) {
			throw new AccessorException( "Failed to create ClassFile accessor factory", t );
		}
	}
}
