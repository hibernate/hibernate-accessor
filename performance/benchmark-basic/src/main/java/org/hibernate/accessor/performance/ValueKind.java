/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance;

/**
 * Whether the accessed property is a primitive ({@code int}) or a reference ({@link String}).
 *
 * <p>The primitive case exposes the boxing cost that separates the strategies, since the accessor
 * API reads and writes values as {@link Object}.
 */
public enum ValueKind {
	PRIMITIVE,
	REFERENCE
}
