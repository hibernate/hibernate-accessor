package org.hibernate.accessor.tck.bytebuddy;

import java.lang.invoke.MethodHandles;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.bytebuddy.ByteBuddyAccessorConfiguration;
import org.hibernate.accessor.bytebuddy.ByteBuddyAccessorFactory;
import org.hibernate.accessor.bytebuddy.ByteBuddyGenerationStrategy;
import org.hibernate.accessor.tck.util.TckAccessorConfiguration;

public class ByteBuddyTckAccessorConfiguration implements TckAccessorConfiguration {

	@Override
	public AccessorFactory factory() {
		ByteBuddyAccessorConfiguration configuration =
				new ByteBuddyAccessorConfiguration( MethodHandles.lookup(), resolveStrategy() );
		return ByteBuddyAccessorFactory.factory( configuration );
	}

	/**
	 * The generation strategy comes from the {@link ByteBuddyCAccessorConfiguration#GENERATION_STRATEGY}
	 * system property so the same TCK can run against every strategy from separate Gradle test tasks;
	 * absent (the default {@code test} task), it falls back to {@link ByteBuddyGenerationStrategy#BULK_SWITCH}.
	 */
	private static ByteBuddyGenerationStrategy resolveStrategy() {
		String value = System.getProperty( ByteBuddyAccessorConfiguration.GENERATION_STRATEGY );
		return value == null || value.isBlank()
				? ByteBuddyGenerationStrategy.BULK_SWITCH
				: ByteBuddyGenerationStrategy.valueOf( value.trim() );
	}
}
