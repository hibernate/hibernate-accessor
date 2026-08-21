package org.hibernate.accessor.tck.tests.beans;

/**
 * A bean whose getter always fails, used to verify how the accessor
 * implementations propagate exceptions raised by the underlying getter.
 */
public class ThrowingGetterBean {

    public static final String FAILURE_MESSAGE = "getter blew up";

    private String value;

    public String getValue() {
        throw new IllegalStateException(FAILURE_MESSAGE);
    }

    public void setValue(String value) {
        this.value = value;
    }
}
