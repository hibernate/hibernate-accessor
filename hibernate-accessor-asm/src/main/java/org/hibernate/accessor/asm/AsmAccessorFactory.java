/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.asm;

import java.lang.invoke.MethodHandles;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.spi.AccessorConfiguration;

/**
 * Entry point for the ASM-based accessor strategy.
 *
 * <p>Creates a factory that generates one bulk accessor class per entity at runtime
 * using ASM bytecode generation with {@code TABLESWITCH} dispatch on field/method index.
 */
public interface AsmAccessorFactory extends AccessorFactory {

	/**
	 * Creates an ASM-based accessor factory using the given lookup for access control.
	 *
	 * @param lookup the lookup object that determines access rights
	 * @return a new ASM-based factory instance
	 */
	static AsmAccessorFactory factory(MethodHandles.Lookup lookup) {
		return factory( new AccessorConfiguration( lookup ) );
	}

	/**
	 * Creates an ASM-based accessor factory using the given configuration.
	 *
	 * @param configuration the accessor configuration (must contain a {@link AccessorConfiguration#LOOKUP lookup})
	 * @return a new ASM-based factory instance
	 */
	static AsmAccessorFactory factory(AccessorConfiguration configuration) {
		return new org.hibernate.accessor.asm.impl.AsmAccessorFactory( configuration );
	}
}
