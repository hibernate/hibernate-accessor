/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.asm;

import java.lang.invoke.MethodHandles;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.asm.impl.AsmBulkSwitchAccessorFactory;
import org.hibernate.accessor.asm.impl.AsmPerMemberAccessorFactory;
import org.hibernate.accessor.spi.AccessorConfiguration;

/**
 * Entry point for the ASM-based accessor strategy.
 *
 * <p>Creates a factory that generates accessor classes at runtime using ASM bytecode generation.
 * The specific generation strategy is determined by the {@link AsmAccessorConfiguration}.
 *
 * <p>Two strategies are available:
 * <ul>
 *   <li>{@link AsmGenerationStrategy#BULK_SWITCH} - One bulk accessor class per entity with TABLESWITCH dispatch (default)</li>
 *   <li>{@link AsmGenerationStrategy#PER_MEMBER} - One dedicated class per field/method accessor</li>
 * </ul>
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
	 * The generation strategy is determined from the configuration.
	 *
	 * @param configuration the accessor configuration (must contain a lookup or framework access context)
	 * @return a new ASM-based factory instance
	 */
	static AsmAccessorFactory factory(AccessorConfiguration configuration) {
		final var strategy = AsmAccessorConfiguration.generationStrategy( configuration );
		return switch ( strategy ) {
			case PER_MEMBER -> new AsmPerMemberAccessorFactory( configuration );
			case BULK_SWITCH -> new AsmBulkSwitchAccessorFactory( configuration );
		};
	}
}
