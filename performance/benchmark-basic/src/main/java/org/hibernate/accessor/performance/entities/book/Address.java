/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.entities.book;

/** Leaf of the customer branch of the {@link Order} graph: all scalar properties, no cascades. */
public class Address {

	private String street;
	private String city;
	private String postalCode;
	private String country;

	public Address() {
	}

	public Address(String street, String city, String postalCode, String country) {
		this.street = street;
		this.city = city;
		this.postalCode = postalCode;
		this.country = country;
	}

	public String getStreet() {
		return street;
	}

	public String getCity() {
		return city;
	}

	public String getPostalCode() {
		return postalCode;
	}

	public String getCountry() {
		return country;
	}
}
