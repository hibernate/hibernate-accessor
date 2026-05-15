package org.hibernate.accessor.asm.impl;

import org.hibernate.accessor.HibernateAccessorValueWriter;

record HibernateAccessorAsmMethodValueWriter(HibernateAccessorAsmBulkAccessor accessor, int index) implements HibernateAccessorValueWriter {

    @Override
    public void set(Object instance, Object value) {
        accessor.writeByMethod(instance, index, value);
    }
}
