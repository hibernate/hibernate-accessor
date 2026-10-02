package org.hibernate.accessor.tck.jpms.closed;

public class GateEntity {
	private String value = "allowed";
	public GateEntity() { }
	public String getValue() { return value; }
}
