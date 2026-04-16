package org.hibernate.accessor.tck.util;

import org.hibernate.accessor.HibernateAccessorFactory;

public interface TckAccessorConfiguration {
    /**
     * Returns the factory implementation to be tested.
     */
    HibernateAccessorFactory factory();
}
