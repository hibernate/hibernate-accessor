/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.entities;

/**
 * A simple entity exposing a primitive ({@code int}) and a reference ({@link String}) property,
 * each reachable via both a field and a getter/setter, plus a full-args constructor.
 *
 * <p>Fields are {@code private} on purpose: the strategies acquire private access via
 * {@code MethodHandles.privateLookupIn}, matching real Hibernate usage.
 */
public class BenchEntity {

	private int intValue;
	private String stringValue;

	public BenchEntity() {
	}

	public BenchEntity(int intValue, String stringValue) {
		this.intValue = intValue;
		this.stringValue = stringValue;
	}

	public int getIntValue() {
		return intValue;
	}

	public void setIntValue(int intValue) {
		this.intValue = intValue;
	}

	public String getStringValue() {
		return stringValue;
	}

	public void setStringValue(String stringValue) {
		this.stringValue = stringValue;
	}
}
