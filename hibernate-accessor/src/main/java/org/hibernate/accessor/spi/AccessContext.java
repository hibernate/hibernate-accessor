/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.spi;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.AccessibleObject;

/**
 * Framework-owned access operations, sharing the authority of {@link #lookup()}.
 * Implement these operations in the framework module with direct Java calls to
 * {@link Module#addReads(Module)} and {@link AccessibleObject#setAccessible(boolean)}.
 * This preserves the framework's caller identity without caller-sensitive method handles,
 * including in native executables. A helper implemented in the accessor module cannot
 * supply the framework's identity.
 *
 * <p>Implementations are trusted capabilities, not authorization boundaries. Return a
 * stable, original lookup from the same module that implements the access operations.
 * Factories retain the context for their lifetime. Implementations must support
 * concurrent calls when their factories are shared between threads.
 */
public interface AccessContext {
	/**
	 * @return the framework's original lookup
	 */
	MethodHandles.Lookup lookup();

	/**
	 * Ensures the framework module reads the target, adding the edge when necessary.
	 * @param target the module to read
	 */
	void ensureReads(Module target);

	/**
	 * Enables reflection using the framework's access rights. Closed packages must
	 * still be rejected according to the JDK's accessibility checks.
	 * @param member the member to make accessible
	 */
	void makeAccessible(AccessibleObject member);

	/**
	 * Creates a private lookup after establishing the required read edge.
	 * @param target the class to access
	 * @return a private lookup for the target
	 * @throws IllegalAccessException if the framework cannot access the target
	 */
	default MethodHandles.Lookup privateLookup(Class<?> target) throws IllegalAccessException {
		ensureReads( target.getModule() );
		return MethodHandles.privateLookupIn( target, lookup() );
	}
}
