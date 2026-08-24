package org.hibernate.accessor.tck.methodhandle;

import org.hibernate.accessor.HibernateAccessorFactory;
import org.hibernate.accessor.tck.util.TckAccessorConfiguration;

import java.lang.invoke.MethodHandles;

public class MethodhandleTckAccessorConfiguration implements TckAccessorConfiguration {
    @Override
    public HibernateAccessorFactory factory() {
        return HibernateAccessorFactory.methodHandle(MethodHandles.lookup());
    }

}
