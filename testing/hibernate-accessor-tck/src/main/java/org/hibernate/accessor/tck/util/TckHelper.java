package org.hibernate.accessor.tck.util;

import org.hibernate.accessor.AccessorFactory;

import java.util.ServiceLoader;

public class TckHelper {

    private static final TckAccessorConfiguration CONFIGURATION;

    static {
        ServiceLoader<TckAccessorConfiguration> loader =
                ServiceLoader.load(TckAccessorConfiguration.class);

        // Find the configuration and extract the factory

        CONFIGURATION = loader.findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "No TckAccessorConfiguration implementation found! " +
                                "Check META-INF/services/util.tck.org.hibernate.accessor.TckAccessorConfiguration"
                ));
    }

    public static AccessorFactory factory() {
        return CONFIGURATION.factory();
    }
}
