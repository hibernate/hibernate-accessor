package org.hibernate.accessor.asm.aot;

public class AotBean {
	private long value;
	private int count;
	private String Ea;
	private String FB;
	public final int constant = 7;
	public static final RuntimeException FAILURE = new RuntimeException( "expected" );

	private AotBean() {
	}

	private AotBean(long value) {
		this.value = value;
	}

	public long getValue() {
		return value;
	}

	public AotBean setValue(long value) {
		this.value = value;
		return this;
	}

	public long value() {
		return value + 100;
	}

	public void value(long value) {
		this.value = value + 10;
	}

	public void value(int value) {
		this.value = value + 20;
	}

	private long fail() {
		throw FAILURE;
	}
}
