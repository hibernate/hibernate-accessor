/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.tck.tests.beans;

public class VarargsBean {
	public String[] strings;
	public Object[] objects;
	public int[] ints;

	public void setStrings(String... values) {
		strings = values;
	}

	public void setObjects(Object... values) {
		objects = values;
	}

	public void setInts(int... values) {
		ints = values;
	}
}
