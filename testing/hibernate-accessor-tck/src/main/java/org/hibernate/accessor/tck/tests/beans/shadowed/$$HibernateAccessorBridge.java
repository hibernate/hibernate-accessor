/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.tck.tests.beans.shadowed;

// A public same-named class is accessible across runtime packages. It must never
// be confused with the package-private helper defined in an entity's own loader.
public class $$HibernateAccessorBridge {
	public static final java.lang.invoke.MethodHandle DEFINE_ACCESSOR_MH = null;
}
