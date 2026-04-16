package org.hibernate.accessor.tck.asm;

import org.hibernate.accessor.HibernateAccessorFactory;
import org.hibernate.accessor.asm.HibernateAccessorAsmFactory;
import org.hibernate.accessor.tck.util.TckAccessorConfiguration;

import java.lang.invoke.MethodHandles;

public class AsmTckAccessorConfiguration implements TckAccessorConfiguration {
    @Override
    public HibernateAccessorFactory factory() {
        return HibernateAccessorAsmFactory.factory(MethodHandles.lookup());
    }
}
