package org.hibernate.accessor.tck.asm;

import org.hibernate.accessor.HibernateAccessorFactory;
import org.hibernate.accessor.asm.HibernateAccessorAsmConfiguration;
import org.hibernate.accessor.asm.HibernateAccessorAsmFactory;
import org.hibernate.accessor.asm.HibernateAccessorAsmGenerationStrategy;
import org.hibernate.accessor.tck.util.TckAccessorConfiguration;

import java.lang.invoke.MethodHandles;

public class AsmTckAccessorConfiguration implements TckAccessorConfiguration {

    @Override
    public HibernateAccessorFactory factory() {
        HibernateAccessorAsmConfiguration configuration =
                new HibernateAccessorAsmConfiguration(MethodHandles.lookup(), resolveStrategy());
        return HibernateAccessorAsmFactory.factory(configuration);
    }

    /**
     * The generation strategy comes from the {@link HibernateAccessorAsmConfiguration#GENERATION_STRATEGY}
     * system property so the same TCK can run against every strategy from separate Gradle test tasks;
     * absent (the default {@code test} task), it falls back to {@link HibernateAccessorAsmGenerationStrategy#BULK_SWITCH}.
     */
    private static HibernateAccessorAsmGenerationStrategy resolveStrategy() {
        String value = System.getProperty(HibernateAccessorAsmConfiguration.GENERATION_STRATEGY);
        return value == null || value.isBlank()
                ? HibernateAccessorAsmGenerationStrategy.BULK_SWITCH
                : HibernateAccessorAsmGenerationStrategy.valueOf(value.trim());
    }
}
