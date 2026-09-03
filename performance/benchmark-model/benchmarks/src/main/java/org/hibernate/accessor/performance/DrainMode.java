/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance;

/**
 * Whether the drain benchmark uses individual single-value accessors (one call per member) or
 * a multi-value accessor (one call for all members at once). This is the axis that separates the
 * Hibernate Validator access pattern (property-by-property) from the Hibernate ORM access pattern
 * (bulk read/write of all columns in a single call).
 */
public enum DrainMode {
	SINGLE_ACCESSOR,
	MULTI_ACCESSOR
}
