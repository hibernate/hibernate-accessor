/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.asm;

/**
 * Selects how the ASM factory generates the bytecode backing single-value readers and writers.
 *
 * <ul>
 *   <li>{@link #BULK_SWITCH} -- one bulk accessor class per entity, with {@code TABLESWITCH}
 *       dispatch on a member index. A single-value reader/writer is a thin record over that bulk
 *       accessor. This shape inlines to a direct field/method access at monomorphic call sites, but
 *       relocates the polymorphism into the bulk-accessor call at megamorphic ones.</li>
 *   <li>{@link #PER_MEMBER} -- one dedicated class per field/getter (or per field/setter), whose body
 *       is a single direct field access or method call with no switch and no shared bulk accessor.
 *       This mirrors what {@code LambdaMetafactory} produces for getters, but also works for fields,
 *       and keeps each generated class monomorphic.</li>
 * </ul>
 */
public enum HibernateAccessorAsmGenerationStrategy {

	/** One bulk accessor per entity with {@code TABLESWITCH} dispatch (the default). */
	BULK_SWITCH,

	/** One dedicated class per member with direct field/method access. */
	PER_MEMBER
}
