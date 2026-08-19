package org.hibernate.accessor.tck.lambda;

import org.hibernate.accessor.HibernateAccessorFactory;
import org.hibernate.accessor.lambda.impl.HibernateAccessorLambdaFactory;
import org.hibernate.accessor.tck.util.TckAccessorConfiguration;

import java.lang.invoke.MethodHandles;

public class LambdaTckAccessorConfiguration implements TckAccessorConfiguration {
    @Override
    public HibernateAccessorFactory factory() {
        return new HibernateAccessorLambdaFactory(MethodHandles.lookup());
    }
}
