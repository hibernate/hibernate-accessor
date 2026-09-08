package org.hibernate.accessor.tck.asm;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.asm.AsmAccessorConfiguration;
import org.hibernate.accessor.asm.AsmAccessorFactory;
import org.hibernate.accessor.asm.AsmGenerationStrategy;
import org.hibernate.accessor.tck.util.TckAccessorConfiguration;

import java.lang.invoke.MethodHandles;

public class AsmTckAccessorConfiguration implements TckAccessorConfiguration {

    @Override
    public AccessorFactory factory() {
        AsmAccessorConfiguration configuration =
                new AsmAccessorConfiguration( MethodHandles.lookup(), resolveStrategy());
        return AsmAccessorFactory.factory(configuration);
    }

    /**
     * The generation strategy comes from the {@link AsmAccessorConfiguration#GENERATION_STRATEGY}
     * system property so the same TCK can run against every strategy from separate Gradle test tasks;
     * absent (the default {@code test} task), it falls back to {@link AsmGenerationStrategy#BULK_SWITCH}.
     */
    private static AsmGenerationStrategy resolveStrategy() {
        String value = System.getProperty( AsmAccessorConfiguration.GENERATION_STRATEGY);
        return value == null || value.isBlank()
                ? AsmGenerationStrategy.BULK_SWITCH
                : AsmGenerationStrategy.valueOf(value.trim());
    }
}
