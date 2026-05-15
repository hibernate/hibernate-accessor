package org.hibernate.accessor.asm.impl;

import org.hibernate.accessor.HibernateAccessorValueReader;

record HibernateAccessorAsmFieldValueReader<T>(HibernateAccessorAsmBulkAccessor accessor, int index) implements HibernateAccessorValueReader<T> {

    @Override
    @SuppressWarnings("unchecked")
    public T get(Object instance) {
        return (T) accessor.readByField(instance, index);
    }
}
