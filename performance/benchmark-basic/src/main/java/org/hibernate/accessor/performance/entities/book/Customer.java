/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.entities.book;

/** Customer branch of the {@link Order} graph: scalar properties plus a cascade into {@link Address}. */
public class Customer {

	private long id;
	private String name;
	private String email;
	private Address address;

	public Customer() {
	}

	public Customer(long id, String name, String email, Address address) {
		this.id = id;
		this.name = name;
		this.email = email;
		this.address = address;
	}

	public long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getEmail() {
		return email;
	}

	public Address getAddress() {
		return address;
	}
}
