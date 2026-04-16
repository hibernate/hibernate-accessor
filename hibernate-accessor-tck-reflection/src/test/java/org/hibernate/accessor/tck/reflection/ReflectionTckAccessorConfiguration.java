package org.hibernate.accessor.tck.reflection;

import org.hibernate.accessor.HibernateAccessorFactory;
import org.hibernate.accessor.reflection.impl.HibernateAccessorReflectionFactory;
import org.hibernate.accessor.tck.util.TckAccessorConfiguration;

public class ReflectionTckAccessorConfiguration implements TckAccessorConfiguration {
    @Override
    public HibernateAccessorFactory factory() {
        return HibernateAccessorReflectionFactory.INSTANCE;
    }
}
