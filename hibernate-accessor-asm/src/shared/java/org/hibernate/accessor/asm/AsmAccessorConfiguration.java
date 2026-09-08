/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.asm;

import java.lang.invoke.MethodHandles;
import java.util.Map;

import org.hibernate.accessor.spi.AccessorConfiguration;

/**
 * ASM-specific {@link AccessorConfiguration} that adds the code-generation strategy flag.
 *
 * <p>The strategy can be supplied either as a {@link AsmGenerationStrategy} value or
 * as its {@link Enum#name() name} string (the latter is convenient when the value originates from a
 * system property); {@link #generationStrategy(AccessorConfiguration)} accepts both.
 */
public class AsmAccessorConfiguration extends AccessorConfiguration {

	/**
	 * Property carrying the {@link AsmGenerationStrategy}. Strategy-neutral on purpose
	 * so the same key reads naturally for both the ASM and ByteBuddy factories.
	 */
	public static final String GENERATION_STRATEGY = "hibernate.accessor.generation.strategy";

	public AsmAccessorConfiguration(MethodHandles.Lookup lookup, AsmGenerationStrategy strategy) {
		super( lookup, Map.of( GENERATION_STRATEGY, strategy ) );
	}

	public AsmAccessorConfiguration(MethodHandles.Lookup lookup, Map<String, Object> properties) {
		super( lookup, properties );
	}

	public AsmAccessorConfiguration(Map<String, Object> properties) {
		super( properties );
	}

	/** The generation strategy configured on this instance, defaulting to {@link AsmGenerationStrategy#BULK_SWITCH}. */
	public AsmGenerationStrategy generationStrategy() {
		return generationStrategy( this );
	}

	/**
	 * Resolves the generation strategy from any configuration, whether or not it is a
	 * {@code AsmConfiguration}. Accepts the property as an enum value or as its name
	 * string, and defaults to {@link AsmGenerationStrategy#BULK_SWITCH} when absent.
	 */
	public static AsmGenerationStrategy generationStrategy(AccessorConfiguration configuration) {
		final Object value = configuration.getProperty( GENERATION_STRATEGY, Object.class );
		if ( value == null ) {
			return AsmGenerationStrategy.BULK_SWITCH;
		}
		if ( value instanceof AsmGenerationStrategy strategy ) {
			return strategy;
		}
		return AsmGenerationStrategy.valueOf( value.toString().trim() );
	}
}
