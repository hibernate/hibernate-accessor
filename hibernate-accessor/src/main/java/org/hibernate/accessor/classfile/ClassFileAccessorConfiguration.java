package org.hibernate.accessor.classfile;

import java.lang.invoke.MethodHandles;
import java.util.Map;

import org.hibernate.accessor.spi.AccessContext;
import org.hibernate.accessor.spi.AccessorConfiguration;
import org.hibernate.accessor.spi.LookupAccess;

/**
 * ClassFile API-specific {@link AccessorConfiguration} that adds the code-generation strategy flag.
 *
 * <p>The strategy can be supplied either as a {@link ClassFileGenerationStrategy} value or
 * as its {@link Enum#name() name} string (the latter is convenient when the value originates from a
 * system property); {@link #generationStrategy(AccessorConfiguration)} accepts both.
 */
public class ClassFileAccessorConfiguration extends AccessorConfiguration {

	/**
	 * Property carrying the {@link ClassFileGenerationStrategy}. Strategy-neutral on purpose
	 * so the same key reads naturally for the bytecode accessor factories.
	 */
	public static final String GENERATION_STRATEGY = "hibernate.accessor.generation.strategy";

	public ClassFileAccessorConfiguration(MethodHandles.Lookup lookup, ClassFileGenerationStrategy strategy) {
		super( lookup, Map.of( GENERATION_STRATEGY, strategy ) );
	}

	public ClassFileAccessorConfiguration(MethodHandles.Lookup lookup) {
		super( lookup, Map.of() );
	}

	public ClassFileAccessorConfiguration(MethodHandles.Lookup lookup, Map<String, Object> properties) {
		super( new LookupAccess( lookup ), properties );
	}

	public ClassFileAccessorConfiguration(AccessContext accessContext, Map<String, Object> properties) {
		super( accessContext, properties );
	}

	/** The generation strategy configured on this instance, defaulting to {@link ClassFileGenerationStrategy#BULK_SWITCH}. */
	public ClassFileGenerationStrategy generationStrategy() {
		return generationStrategy( this );
	}

	/**
	 * Resolves the generation strategy from any configuration, whether or not it is a
	 * {@code ClassFileAccessorConfiguration}. Accepts the property as an enum value or as its name
	 * string, and defaults to {@link ClassFileGenerationStrategy#BULK_SWITCH} when absent.
	 */
	public static ClassFileGenerationStrategy generationStrategy(AccessorConfiguration configuration) {
		final Object value = configuration.getProperty( GENERATION_STRATEGY, Object.class );
		if ( value == null ) {
			return ClassFileGenerationStrategy.BULK_SWITCH;
		}
		if ( value instanceof ClassFileGenerationStrategy strategy ) {
			return strategy;
		}
		return ClassFileGenerationStrategy.valueOf( value.toString().trim() );
	}
}
