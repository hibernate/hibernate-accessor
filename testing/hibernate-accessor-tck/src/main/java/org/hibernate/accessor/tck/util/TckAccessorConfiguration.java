package org.hibernate.accessor.tck.util;

import org.hibernate.accessor.AccessorFactory;

public interface TckAccessorConfiguration {
    /**
     * Returns the factory implementation to be tested.
     */
    AccessorFactory factory();
}
