/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.entities.book;

/** Leaf of the book branch of the {@link Order} graph: all scalar properties, no cascades. */
public class Publisher {

	private String name;
	private int foundedYear;

	public Publisher() {
	}

	public Publisher(String name, int foundedYear) {
		this.name = name;
		this.foundedYear = foundedYear;
	}

	public String getName() {
		return name;
	}

	public int getFoundedYear() {
		return foundedYear;
	}
}
