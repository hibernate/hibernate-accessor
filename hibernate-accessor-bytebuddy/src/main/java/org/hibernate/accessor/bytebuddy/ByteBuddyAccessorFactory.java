/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.bytebuddy;

import java.lang.invoke.MethodHandles;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.bytebuddy.impl.ByteBuddyBulkSwitchAccessorFactory;
import org.hibernate.accessor.bytebuddy.impl.ByteBuddyPerMemberAccessorFactory;
import org.hibernate.accessor.spi.AccessorConfiguration;

/**
 * Entry point for the ByteBuddy-based accessor strategy.
 *
 * <p>Creates a factory that generates accessor classes at runtime using ByteBuddy bytecode generation.
 * The specific generation strategy is determined by the {@link ByteBuddyAccessorConfiguration}.
 *
 * <p>Two strategies are available:
 * <ul>
 *   <li>{@link ByteBuddyGenerationStrategy#BULK_SWITCH} - One bulk accessor class per entity with {@code TABLESWITCH} dispatch (default)</li>
 *   <li>{@link ByteBuddyGenerationStrategy#PER_MEMBER} - One dedicated class per field/method accessor</li>
 * </ul>
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
	 * The generation strategy is determined from the configuration.
	 *
	 * @param configuration the accessor configuration (must contain a {@link AccessorConfiguration#LOOKUP lookup})
	 * @return a new ByteBuddy-based factory instance
	 */
	static ByteBuddyAccessorFactory factory(AccessorConfiguration configuration) {
		ByteBuddyGenerationStrategy strategy = ByteBuddyAccessorConfiguration.generationStrategy( configuration );
		return switch ( strategy ) {
			case PER_MEMBER -> new ByteBuddyPerMemberAccessorFactory( configuration );
			case BULK_SWITCH -> new ByteBuddyBulkSwitchAccessorFactory( configuration );
		};
	}
}
