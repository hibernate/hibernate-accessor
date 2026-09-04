/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.entities.book;

import java.util.List;

/**
 * Root of the book-order object graph traversed by {@code CascadeBenchmark}.
 *
 * <p>Cascades into a single {@link Customer} and into a collection of {@link OrderLine}s, giving the
 * traversal both a one-to-one and a one-to-many edge -- the shape a validator or ORM dirty-check walks.
 */
public class Order {

	private long id;
	private String orderNumber;
	private int itemCount;
	private Customer customer;
	private List<OrderLine> lines;

	public Order() {
	}

	public Order(long id, String orderNumber, int itemCount, Customer customer, List<OrderLine> lines) {
		this.id = id;
		this.orderNumber = orderNumber;
		this.itemCount = itemCount;
		this.customer = customer;
		this.lines = lines;
	}

	public long getId() {
		return id;
	}

	public String getOrderNumber() {
		return orderNumber;
	}

	public int getItemCount() {
		return itemCount;
	}

	public Customer getCustomer() {
		return customer;
	}

	public List<OrderLine> getLines() {
		return lines;
	}
}
