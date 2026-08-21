package org.hibernate.accessor.tck.tests.exception;

import org.hibernate.accessor.HibernateAccessorException;
import org.hibernate.accessor.HibernateAccessorFactory;
import org.hibernate.accessor.HibernateAccessorValueWriter;
import org.hibernate.accessor.tck.tests.beans.ThrowingSetterBean;
import org.hibernate.accessor.tck.util.TckHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Exception thrown by a setter")
public class ThrowingSetterAccessTest {

    private HibernateAccessorFactory factory;

    @BeforeAll
    void setup() {
        factory = TckHelper.factory();
    }

    @Test
    void testSetterExceptionIsWrapped() throws Exception {
        ThrowingSetterBean bean = new ThrowingSetterBean();
        Method setter = ThrowingSetterBean.class.getDeclaredMethod("setValue", String.class);
        setter.setAccessible(true);

        HibernateAccessorValueWriter writer = factory.valueWriter(setter);

        HibernateAccessorException thrown =
                assertThrows(HibernateAccessorException.class, () -> writer.set(bean, "ignored"));

        Throwable cause = thrown.getCause();
        assertInstanceOf(IllegalStateException.class, cause);
        assertEquals(ThrowingSetterBean.FAILURE_MESSAGE, cause.getMessage());
    }
}
