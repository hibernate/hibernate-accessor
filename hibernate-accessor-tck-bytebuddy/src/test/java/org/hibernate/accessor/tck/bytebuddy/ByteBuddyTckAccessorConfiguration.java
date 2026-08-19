package org.hibernate.accessor.tck.bytebuddy;

import org.hibernate.accessor.HibernateAccessorFactory;
import org.hibernate.accessor.bytebuddy.HibernateAccessorByteBuddyFactory;
import org.hibernate.accessor.tck.util.TckAccessorConfiguration;

import java.lang.invoke.MethodHandles;

public class ByteBuddyTckAccessorConfiguration implements TckAccessorConfiguration {
    @Override
    public HibernateAccessorFactory factory() {
        return HibernateAccessorByteBuddyFactory.factory(MethodHandles.lookup());
    }
}
