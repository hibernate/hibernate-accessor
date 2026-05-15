package org.hibernate.accessor.asm.impl;

import org.hibernate.accessor.HibernateAccessorValueWriter;

record HibernateAccessorAsmFieldValueWriter(HibernateAccessorAsmBulkAccessor accessor, int index) implements HibernateAccessorValueWriter {

    @Override
    public void set(Object instance, Object value) {
        accessor.writeByField(instance, index, value);
    }
}
