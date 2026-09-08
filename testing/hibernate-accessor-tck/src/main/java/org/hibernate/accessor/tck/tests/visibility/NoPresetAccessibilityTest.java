package org.hibernate.accessor.tck.tests.visibility;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.Instantiator;
import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.tck.tests.beans.visibility.PropertyVisibilityBean;
import org.hibernate.accessor.tck.util.TckHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Access without caller pre-setting setAccessible")
public class NoPresetAccessibilityTest {

    private AccessorFactory factory;

    @BeforeAll
    void setup() {
        factory = TckHelper.factory();
    }

    @Test
    void testPrivateFieldAccessWithoutSetAccessible() throws Exception {
        Field field = PropertyVisibilityBean.class.getDeclaredField("privateField");

        ValueWriter writer = factory.valueWriter(field);
        ValueReader<?> reader = factory.valueReader(field);

        PropertyVisibilityBean bean = new PropertyVisibilityBean();
        writer.set(bean, "no-preset");
        assertEquals("no-preset", reader.get(bean));
    }

    @Test
    void testPrivateMethodAccessWithoutSetAccessible() throws Exception {
        Method setter = PropertyVisibilityBean.class.getDeclaredMethod("setPrivateField", String.class);
        Method getter = PropertyVisibilityBean.class.getDeclaredMethod("getPrivateField");

        ValueWriter writer = factory.valueWriter(setter);
        ValueReader<?> reader = factory.valueReader(getter);

        PropertyVisibilityBean bean = new PropertyVisibilityBean();
        writer.set(bean, "no-preset");
        assertEquals("no-preset", reader.get(bean));
    }

    @Test
    void testPackagePrivateFieldAccessWithoutSetAccessible() throws Exception {
        Field field = PropertyVisibilityBean.class.getDeclaredField("defaultField");

        ValueWriter writer = factory.valueWriter(field);
        ValueReader<?> reader = factory.valueReader(field);

        PropertyVisibilityBean bean = new PropertyVisibilityBean();
        writer.set(bean, "pkg-private");
        assertEquals("pkg-private", reader.get(bean));
    }

    @Test
    void testProtectedFieldAccessWithoutSetAccessible() throws Exception {
        Field field = PropertyVisibilityBean.class.getDeclaredField("protectedField");

        ValueWriter writer = factory.valueWriter(field);
        ValueReader<?> reader = factory.valueReader(field);

        PropertyVisibilityBean bean = new PropertyVisibilityBean();
        writer.set(bean, "protected-val");
        assertEquals("protected-val", reader.get(bean));
    }

    @Test
    void testConstructorWithoutSetAccessible() throws Exception {
        Constructor<?> constructor = PropertyVisibilityBean.class.getDeclaredConstructor();

        Instantiator<?> instantiator = factory.instantiator(constructor);
        Object instance = instantiator.create();
        assertNotNull(instance);
        assertEquals(PropertyVisibilityBean.class, instance.getClass());
    }

	@Test
	void testPackagePrivateConstructorWithoutSetAccessible() throws Exception {
		Class<?> beanClass = Class.forName( "org.hibernate.accessor.tck.tests.beans.visibility.PackagePrivateBean" );
		Constructor<?> constructor = beanClass.getDeclaredConstructor();

		Instantiator<?> instantiator = factory.instantiator( constructor );
		Object instance = instantiator.create();
		assertNotNull( instance );
		assertEquals( beanClass, instance.getClass() );
	}
}
