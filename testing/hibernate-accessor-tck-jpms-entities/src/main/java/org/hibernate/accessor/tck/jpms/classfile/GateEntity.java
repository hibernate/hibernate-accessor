package org.hibernate.accessor.tck.jpms.classfile;

public class GateEntity {
	private String value = "allowed";
	public GateEntity() { }
	public String getValue() { return value; }
	public static java.lang.invoke.MethodHandles.Lookup lookup() { return java.lang.invoke.MethodHandles.lookup(); }
}
