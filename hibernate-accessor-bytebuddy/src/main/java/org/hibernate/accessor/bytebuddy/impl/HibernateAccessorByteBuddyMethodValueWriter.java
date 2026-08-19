/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.bytebuddy.impl;

import org.hibernate.accessor.HibernateAccessorValueWriter;
import org.hibernate.accessor.bytebuddy.spi.HibernateAccessorByteBuddyBulkAccessor;

record HibernateAccessorByteBuddyMethodValueWriter(HibernateAccessorByteBuddyBulkAccessor accessor, int index) implements HibernateAccessorValueWriter {

	@Override
	public void set(Object instance, Object value) {
		accessor.writeByMethod( instance, index, value );
	}
}
