/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.bytebuddy;

import java.lang.invoke.MethodHandles;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.spi.AccessorConfiguration;

/**
 * Entry point for the ByteBuddy-based accessor strategy.
 *
 * <p>Creates a factory that generates one bulk accessor class per entity at runtime
 * using ByteBuddy's shaded ASM bytecode generation with {@code TABLESWITCH} dispatch
 * on field/method index.
 */
public interface ByteBuddyAccessorFactory extends AccessorFactory {

	/**
	 * Creates a ByteBuddy-based accessor factory using the given lookup for access control.
	 *
	 * @param lookup the lookup object that determines access rights
	 * @return a new ByteBuddy-based factory instance
	 */
	static ByteBuddyAccessorFactory factory(MethodHandles.Lookup lookup) {
		return factory( new AccessorConfiguration( lookup ) );
	}

	/**
	 * Creates a ByteBuddy-based accessor factory using the given configuration.
	 *
	 * @param configuration the accessor configuration (must contain a {@link AccessorConfiguration#LOOKUP lookup})
	 * @return a new ByteBuddy-based factory instance
	 */
	static ByteBuddyAccessorFactory factory(AccessorConfiguration configuration) {
		return new org.hibernate.accessor.bytebuddy.impl.ByteBuddyAccessorFactory( configuration );
	}
}
