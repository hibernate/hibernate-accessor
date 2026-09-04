/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance;

/**
 * Whether a property is accessed through its field or through its getter/setter method.
 *
 * <p><strong>Duplicated</strong> verbatim in the {@code hibernate-accessor-benchmark-basic} and
 * {@code hibernate-accessor-benchmark-model} modules (the two never share a classpath). Keep the
 * copies in sync.
 */
public enum AccessKind {
	FIELD,
	METHOD
}
