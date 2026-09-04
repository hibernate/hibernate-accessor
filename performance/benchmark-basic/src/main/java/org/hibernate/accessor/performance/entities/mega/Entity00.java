/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.entities.mega;

/** Distinct entity type #00, used to build a megamorphic accessor call site. */
public class Entity00 {

	private int value;

	public Entity00() {
	}

	public Entity00(int value) {
		this.value = value;
	}

	public int getValue() {
		return value;
	}

	public void setValue(int value) {
		this.value = value;
	}
}
