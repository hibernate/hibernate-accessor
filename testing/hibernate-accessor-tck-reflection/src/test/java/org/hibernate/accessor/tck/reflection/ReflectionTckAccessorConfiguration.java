package org.hibernate.accessor.tck.reflection;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.reflection.impl.ReflectionAccessorFactory;
import org.hibernate.accessor.tck.util.TckAccessorConfiguration;

public class ReflectionTckAccessorConfiguration implements TckAccessorConfiguration {
    @Override
    public AccessorFactory factory() {
        return ReflectionAccessorFactory.INSTANCE;
    }
}
