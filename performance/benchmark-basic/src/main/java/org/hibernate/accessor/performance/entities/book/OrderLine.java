/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.entities.book;

/** A line of an {@link Order}: scalar quantity/total plus a cascade into {@link Book}. */
public class OrderLine {

	private int quantity;
	private double lineTotal;
	private Book book;

	public OrderLine() {
	}

	public OrderLine(int quantity, double lineTotal, Book book) {
		this.quantity = quantity;
		this.lineTotal = lineTotal;
		this.book = book;
	}

	public int getQuantity() {
		return quantity;
	}

	public double getLineTotal() {
		return lineTotal;
	}

	public Book getBook() {
		return book;
	}
}
