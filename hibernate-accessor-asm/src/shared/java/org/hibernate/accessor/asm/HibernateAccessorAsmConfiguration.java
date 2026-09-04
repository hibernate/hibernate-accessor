/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.asm;

import java.lang.invoke.MethodHandles;
import java.util.Map;

import org.hibernate.accessor.spi.HibernateAccessorConfiguration;

/**
 * ASM-specific {@link HibernateAccessorConfiguration} that adds the code-generation strategy flag.
 *
 * <p>The strategy can be supplied either as a {@link HibernateAccessorAsmGenerationStrategy} value or
 * as its {@link Enum#name() name} string (the latter is convenient when the value originates from a
 * system property); {@link #generationStrategy(HibernateAccessorConfiguration)} accepts both.
 */
public class HibernateAccessorAsmConfiguration extends HibernateAccessorConfiguration {

	/**
	 * Property carrying the {@link HibernateAccessorAsmGenerationStrategy}. Strategy-neutral on purpose
	 * so the same key reads naturally for both the ASM and ByteBuddy factories.
	 */
	public static final String GENERATION_STRATEGY = "hibernate.accessor.generation.strategy";

	public HibernateAccessorAsmConfiguration(MethodHandles.Lookup lookup, HibernateAccessorAsmGenerationStrategy strategy) {
		super( lookup, Map.of( GENERATION_STRATEGY, strategy ) );
	}

	public HibernateAccessorAsmConfiguration(MethodHandles.Lookup lookup, Map<String, Object> properties) {
		super( lookup, properties );
	}

	public HibernateAccessorAsmConfiguration(Map<String, Object> properties) {
		super( properties );
	}

	/** The generation strategy configured on this instance, defaulting to {@link HibernateAccessorAsmGenerationStrategy#BULK_SWITCH}. */
	public HibernateAccessorAsmGenerationStrategy generationStrategy() {
		return generationStrategy( this );
	}

	/**
	 * Resolves the generation strategy from any configuration, whether or not it is a
	 * {@code HibernateAccessorAsmConfiguration}. Accepts the property as an enum value or as its name
	 * string, and defaults to {@link HibernateAccessorAsmGenerationStrategy#BULK_SWITCH} when absent.
	 */
	public static HibernateAccessorAsmGenerationStrategy generationStrategy(HibernateAccessorConfiguration configuration) {
		final Object value = configuration.getProperty( GENERATION_STRATEGY, Object.class );
		if ( value == null ) {
			return HibernateAccessorAsmGenerationStrategy.BULK_SWITCH;
		}
		if ( value instanceof HibernateAccessorAsmGenerationStrategy strategy ) {
			return strategy;
		}
		return HibernateAccessorAsmGenerationStrategy.valueOf( value.toString().trim() );
	}
}
