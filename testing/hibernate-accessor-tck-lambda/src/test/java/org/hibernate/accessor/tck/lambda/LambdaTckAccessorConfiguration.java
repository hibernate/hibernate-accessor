package org.hibernate.accessor.tck.lambda;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.lambda.impl.LambdaAccessorFactory;
import org.hibernate.accessor.tck.util.TckAccessorConfiguration;

import java.lang.invoke.MethodHandles;

public class LambdaTckAccessorConfiguration implements TckAccessorConfiguration {
    @Override
    public AccessorFactory factory() {
        return new LambdaAccessorFactory(MethodHandles.lookup());
    }
}
