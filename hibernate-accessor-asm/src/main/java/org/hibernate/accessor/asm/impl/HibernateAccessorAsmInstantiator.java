package org.hibernate.accessor.asm.impl;

import org.hibernate.accessor.HibernateAccessorInstantiator;

record HibernateAccessorAsmInstantiator<T>(HibernateAccessorAsmBulkAccessor accessor, int index) implements HibernateAccessorInstantiator<T> {

    @Override
    @SuppressWarnings("unchecked")
    public T create(Object... args) {
        return (T) accessor.newInstance(index, args);
    }
}
