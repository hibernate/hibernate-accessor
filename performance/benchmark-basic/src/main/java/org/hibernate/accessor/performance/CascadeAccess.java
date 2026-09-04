/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance;

/**
 * How a cascading traversal addresses each property: all via fields, all via getters, or a realistic
 * mix of the two.
 */
public enum CascadeAccess {
	FIELD,
	METHOD,
	MIXED
}
