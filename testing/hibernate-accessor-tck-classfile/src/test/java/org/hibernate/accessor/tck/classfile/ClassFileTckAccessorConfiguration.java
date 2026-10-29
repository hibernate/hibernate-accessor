package org.hibernate.accessor.tck.classfile;

import java.lang.invoke.MethodHandles;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.classfile.ClassFileAccessorConfiguration;
import org.hibernate.accessor.classfile.ClassFileGenerationStrategy;
import org.hibernate.accessor.tck.util.TckAccessorConfiguration;

public class ClassFileTckAccessorConfiguration implements TckAccessorConfiguration {

	@Override
	public AccessorFactory factory() {
		ClassFileAccessorConfiguration configuration =
				new ClassFileAccessorConfiguration( MethodHandles.lookup(), resolveStrategy() );
		return AccessorFactory.classFile( configuration );
	}

	/**
	 * The generation strategy comes from the {@link ClassFileAccessorConfiguration#GENERATION_STRATEGY}
	 * system property so the same TCK can run against every strategy from separate Gradle test tasks;
	 * absent (the default {@code test} task), it falls back to {@link ClassFileGenerationStrategy#BULK_SWITCH}.
	 */
	private static ClassFileGenerationStrategy resolveStrategy() {
		String value = System.getProperty( ClassFileAccessorConfiguration.GENERATION_STRATEGY );
		return value == null || value.isBlank()
				? ClassFileGenerationStrategy.BULK_SWITCH
				: ClassFileGenerationStrategy.valueOf( value.trim() );
	}
}
