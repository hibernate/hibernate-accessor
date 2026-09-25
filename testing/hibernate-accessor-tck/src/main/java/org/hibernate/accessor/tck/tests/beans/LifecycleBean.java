/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.tck.tests.beans;

import java.lang.invoke.MethodHandles;

public class LifecycleBean {
	public String value;
	public LifecycleBean() { }
	public String getValue() { return value; }
	public void setValue(String value) { this.value = value; }
	public static MethodHandles.Lookup lookup() { return MethodHandles.lookup(); }
}
