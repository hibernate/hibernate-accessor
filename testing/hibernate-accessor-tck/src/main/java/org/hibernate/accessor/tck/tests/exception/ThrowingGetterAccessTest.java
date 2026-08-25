package org.hibernate.accessor.tck.tests.exception;

import org.hibernate.accessor.HibernateAccessorException;
import org.hibernate.accessor.HibernateAccessorFactory;
import org.hibernate.accessor.HibernateAccessorValueReader;
import org.hibernate.accessor.tck.tests.beans.ThrowingGetterBean;
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
@DisplayName("Exception thrown by a getter")
public class ThrowingGetterAccessTest {

    private HibernateAccessorFactory factory;

    @BeforeAll
    void setup() {
        factory = TckHelper.factory();
    }

    @Test
    void testGetterExceptionIsWrapped() throws Exception {
        ThrowingGetterBean bean = new ThrowingGetterBean();
        Method getter = ThrowingGetterBean.class.getDeclaredMethod("getValue");
        getter.setAccessible(true);

        HibernateAccessorValueReader<?> reader = factory.valueReader(getter);

        HibernateAccessorException thrown =
                assertThrows(HibernateAccessorException.class, () -> reader.get(bean));

        Throwable cause = thrown.getCause();
        assertInstanceOf(IllegalStateException.class, cause);
        assertEquals(ThrowingGetterBean.FAILURE_MESSAGE, cause.getMessage());
    }
}
