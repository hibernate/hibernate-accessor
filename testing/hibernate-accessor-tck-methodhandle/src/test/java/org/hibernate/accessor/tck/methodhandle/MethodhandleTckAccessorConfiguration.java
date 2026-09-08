package org.hibernate.accessor.tck.methodhandle;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.tck.util.TckAccessorConfiguration;

import java.lang.invoke.MethodHandles;

public class MethodhandleTckAccessorConfiguration implements TckAccessorConfiguration {
    @Override
    public AccessorFactory factory() {
        return AccessorFactory.methodHandle(MethodHandles.lookup());
    }

}
