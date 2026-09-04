package org.hibernate.accessor.tck.bytebuddy;

import org.hibernate.accessor.HibernateAccessorFactory;
import org.hibernate.accessor.bytebuddy.HibernateAccessorByteBuddyConfiguration;
import org.hibernate.accessor.bytebuddy.HibernateAccessorByteBuddyFactory;
import org.hibernate.accessor.bytebuddy.HibernateAccessorByteBuddyGenerationStrategy;
import org.hibernate.accessor.tck.util.TckAccessorConfiguration;

import java.lang.invoke.MethodHandles;

public class ByteBuddyTckAccessorConfiguration implements TckAccessorConfiguration {

    @Override
    public HibernateAccessorFactory factory() {
        HibernateAccessorByteBuddyConfiguration configuration =
                new HibernateAccessorByteBuddyConfiguration(MethodHandles.lookup(), resolveStrategy());
        return HibernateAccessorByteBuddyFactory.factory(configuration);
    }

    /**
     * The generation strategy comes from the {@link HibernateAccessorByteBuddyConfiguration#GENERATION_STRATEGY}
     * system property so the same TCK can run against every strategy from separate Gradle test tasks;
     * absent (the default {@code test} task), it falls back to {@link HibernateAccessorByteBuddyGenerationStrategy#BULK_SWITCH}.
     */
    private static HibernateAccessorByteBuddyGenerationStrategy resolveStrategy() {
        String value = System.getProperty(HibernateAccessorByteBuddyConfiguration.GENERATION_STRATEGY);
        return value == null || value.isBlank()
                ? HibernateAccessorByteBuddyGenerationStrategy.BULK_SWITCH
                : HibernateAccessorByteBuddyGenerationStrategy.valueOf(value.trim());
    }
}
