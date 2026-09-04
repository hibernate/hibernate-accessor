/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.entities.mega;

/** Distinct entity type #10, used to build a megamorphic accessor call site. */
public class Entity10 {

	private int value;

	public Entity10() {
	}

	public Entity10(int value) {
		this.value = value;
	}

	public int getValue() {
		return value;
	}

	public void setValue(int value) {
		this.value = value;
	}
}
