/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.entities.mega;

/** Distinct entity type #09, used to build a megamorphic accessor call site. */
public class Entity09 {

	private int value;

	public Entity09() {
	}

	public Entity09(int value) {
		this.value = value;
	}

	public int getValue() {
		return value;
	}

	public void setValue(int value) {
		this.value = value;
	}
}
