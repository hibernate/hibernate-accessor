package org.hibernate.accessor.tck.tests.beans;

/**
 * A bean whose setter always fails, used to verify how the accessor
 * implementations propagate exceptions raised by the underlying setter.
 */
public class ThrowingSetterBean {

    public static final String FAILURE_MESSAGE = "setter blew up";

    private String value;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        throw new IllegalStateException(FAILURE_MESSAGE);
    }
}
