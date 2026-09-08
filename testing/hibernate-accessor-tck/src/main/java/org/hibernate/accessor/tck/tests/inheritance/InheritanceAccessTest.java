package org.hibernate.accessor.tck.tests.inheritance;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.tck.tests.beans.inheritance.ChildBean;
import org.hibernate.accessor.tck.tests.beans.inheritance.ParentBean;
import org.hibernate.accessor.tck.util.TckHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Inheritance field and method access")
public class InheritanceAccessTest {

    private AccessorFactory factory;

    @BeforeAll
    void setup() {
        factory = TckHelper.factory();
    }

    @Test
    void testAccessInheritedFieldOnChildInstance() throws Exception {
        Field field = ParentBean.class.getDeclaredField("parentField");
        field.setAccessible(true);

        ValueWriter writer = factory.valueWriter(field);
        ValueReader<?> reader = factory.valueReader(field);

        ChildBean child = new ChildBean();
        writer.set(child, "inherited-value");
        assertEquals("inherited-value", reader.get(child));
    }

    @Test
    void testAccessInheritedMethodOnChildInstance() throws Exception {
        Method setter = ParentBean.class.getDeclaredMethod("setParentField", String.class);
        Method getter = ParentBean.class.getDeclaredMethod("getParentField");
        setter.setAccessible(true);
        getter.setAccessible(true);

        ValueWriter writer = factory.valueWriter(setter);
        ValueReader<?> reader = factory.valueReader(getter);

        ChildBean child = new ChildBean();
        writer.set(child, "method-inherited");
        assertEquals("method-inherited", reader.get(child));
    }

    @Test
    void testAccessChildFieldOnChildInstance() throws Exception {
        Field field = ChildBean.class.getDeclaredField("childField");
        field.setAccessible(true);

        ValueWriter writer = factory.valueWriter(field);
        ValueReader<?> reader = factory.valueReader(field);

        ChildBean child = new ChildBean();
        writer.set(child, "child-value");
        assertEquals("child-value", reader.get(child));
    }

    @Test
    void testAccessBothParentAndChildFields() throws Exception {
        Field parentField = ParentBean.class.getDeclaredField("parentField");
        parentField.setAccessible(true);
        Field childField = ChildBean.class.getDeclaredField("childField");
        childField.setAccessible(true);

        ValueWriter parentWriter = factory.valueWriter(parentField);
        ValueReader<?> parentReader = factory.valueReader(parentField);
        ValueWriter childWriter = factory.valueWriter(childField);
        ValueReader<?> childReader = factory.valueReader(childField);

        ChildBean child = new ChildBean();
        parentWriter.set(child, "parent-val");
        childWriter.set(child, "child-val");

        assertEquals("parent-val", parentReader.get(child));
        assertEquals("child-val", childReader.get(child));
    }
}
