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
