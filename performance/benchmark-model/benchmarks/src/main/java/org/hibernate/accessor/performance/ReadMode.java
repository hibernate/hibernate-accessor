/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance;

/**
 * How many of each entity's scalar members the walk actually reads -- the read-hotness axis.
 *
 * <p>{@link #ALL} reads every scalar, exercising the full member switch uniformly. {@link #HOT_SUBSET}
 * reads only the first {@link #HOT_SCALARS} scalars of each type, so a handful of members dominate.
 * This is the decider between the whole-model double-switch (one monomorphic {@code get()} site, one
 * {@code tableswitch} regardless of hotness) and per-member code generation (a dedicated site per
 * member that can go individually hot and inline): the two rank differently as reads concentrate.
 */
public enum ReadMode {

	ALL {
		@Override
		public int hotScalarCount(int fieldCount) {
			return fieldCount;
		}
	},
	HOT_SUBSET {
		@Override
		public int hotScalarCount(int fieldCount) {
			return Math.min( fieldCount, HOT_SCALARS );
		}
	};

	/** The number of hot scalars per type under {@link #HOT_SUBSET}. */
	static final int HOT_SCALARS = 2;

	/**
	 * The number of scalar members read per entity for this mode, given the type's total scalar count.
	 * References are always followed in full; only scalar read breadth varies, isolating hotness.
	 */
	public abstract int hotScalarCount(int fieldCount);
}
