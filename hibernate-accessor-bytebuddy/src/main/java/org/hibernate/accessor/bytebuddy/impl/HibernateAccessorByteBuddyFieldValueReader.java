/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.bytebuddy.impl;

import org.hibernate.accessor.HibernateAccessorValueReader;

record HibernateAccessorByteBuddyFieldValueReader<T>(HibernateAccessorByteBuddyBulkAccessor accessor, int index) implements HibernateAccessorValueReader<T> {

	@Override
	@SuppressWarnings("unchecked")
	public T get(Object instance) {
		return (T) accessor.readByField(instance, index);
	}
}
