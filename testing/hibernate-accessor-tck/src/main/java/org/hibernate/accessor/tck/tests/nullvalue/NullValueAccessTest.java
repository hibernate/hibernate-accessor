package org.hibernate.accessor.tck.tests.nullvalue;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.tck.tests.beans.visibility.PropertyVisibilityBean;
import org.hibernate.accessor.tck.util.TckHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Null value handling")
public class NullValueAccessTest {

    private AccessorFactory factory;

    @BeforeAll
    void setup() {
        factory = TckHelper.factory();
    }

    @Test
    void testReadDefaultNullFieldValue() throws Exception {
        PropertyVisibilityBean bean = new PropertyVisibilityBean();
        Field field = PropertyVisibilityBean.class.getDeclaredField("publicField");
        field.setAccessible(true);

        ValueReader<?> reader = factory.valueReader(field);
        assertNull(reader.get(bean));
    }

    @Test
    void testWriteAndReadNullFieldValue() throws Exception {
        PropertyVisibilityBean bean = new PropertyVisibilityBean();
        Field field = PropertyVisibilityBean.class.getDeclaredField("publicField");
        field.setAccessible(true);

        ValueWriter writer = factory.valueWriter(field);
        ValueReader<?> reader = factory.valueReader(field);

        writer.set(bean, "not-null");
        assertEquals("not-null", reader.get(bean));

        writer.set(bean, null);
        assertNull(reader.get(bean));
    }

    @Test
    void testReadDefaultNullMethodValue() throws Exception {
        PropertyVisibilityBean bean = new PropertyVisibilityBean();
        Method getter = PropertyVisibilityBean.class.getDeclaredMethod("getPublicField");
        getter.setAccessible(true);

        ValueReader<?> reader = factory.valueReader(getter);
        assertNull(reader.get(bean));
    }

    @Test
    void testWriteAndReadNullMethodValue() throws Exception {
        PropertyVisibilityBean bean = new PropertyVisibilityBean();
        Method setter = PropertyVisibilityBean.class.getDeclaredMethod("setPublicField", String.class);
        Method getter = PropertyVisibilityBean.class.getDeclaredMethod("getPublicField");
        setter.setAccessible(true);
        getter.setAccessible(true);

        ValueWriter writer = factory.valueWriter(setter);
        ValueReader<?> reader = factory.valueReader(getter);

        writer.set(bean, "not-null");
        assertEquals("not-null", reader.get(bean));

        writer.set(bean, null);
        assertNull(reader.get(bean));
    }
}
