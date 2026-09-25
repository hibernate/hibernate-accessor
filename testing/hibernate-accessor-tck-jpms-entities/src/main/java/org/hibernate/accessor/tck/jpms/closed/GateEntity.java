/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.tck.jpms.closed;

public class GateEntity {
	private String value = "allowed";
	public GateEntity() { }
	public String getValue() { return value; }
}
