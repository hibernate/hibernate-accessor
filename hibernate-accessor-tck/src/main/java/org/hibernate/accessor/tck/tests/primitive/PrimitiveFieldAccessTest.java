package org.hibernate.accessor.tck.tests.primitive;

import org.hibernate.accessor.HibernateAccessorFactory;
import org.hibernate.accessor.HibernateAccessorValueReader;
import org.hibernate.accessor.HibernateAccessorValueWriter;
import org.hibernate.accessor.tck.tests.beans.PrimitiveFieldBean;
import org.hibernate.accessor.tck.util.TckHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Primitive type field and method access")
public class PrimitiveFieldAccessTest {

    private HibernateAccessorFactory factory;

    @BeforeAll
    void setup() {
        factory = TckHelper.factory();
    }

    @Test
    void testIntFieldAccess() throws Exception {
        PrimitiveFieldBean bean = new PrimitiveFieldBean();
        Field field = PrimitiveFieldBean.class.getDeclaredField("intField");
        field.setAccessible(true);

        HibernateAccessorValueWriter writer = factory.valueWriter(field);
        HibernateAccessorValueReader<?> reader = factory.valueReader(field);

        writer.set(bean, 42);
        assertEquals(42, reader.get(bean));
    }

    @Test
    void testLongFieldAccess() throws Exception {
        PrimitiveFieldBean bean = new PrimitiveFieldBean();
        Field field = PrimitiveFieldBean.class.getDeclaredField("longField");
        field.setAccessible(true);

        HibernateAccessorValueWriter writer = factory.valueWriter(field);
        HibernateAccessorValueReader<?> reader = factory.valueReader(field);

        writer.set(bean, 123456789L);
        assertEquals(123456789L, reader.get(bean));
    }

    @Test
    void testBooleanFieldAccess() throws Exception {
        PrimitiveFieldBean bean = new PrimitiveFieldBean();
        Field field = PrimitiveFieldBean.class.getDeclaredField("booleanField");
        field.setAccessible(true);

        HibernateAccessorValueWriter writer = factory.valueWriter(field);
        HibernateAccessorValueReader<?> reader = factory.valueReader(field);

        writer.set(bean, true);
        assertEquals(true, reader.get(bean));
    }

    @Test
    void testDoubleFieldAccess() throws Exception {
        PrimitiveFieldBean bean = new PrimitiveFieldBean();
        Field field = PrimitiveFieldBean.class.getDeclaredField("doubleField");
        field.setAccessible(true);

        HibernateAccessorValueWriter writer = factory.valueWriter(field);
        HibernateAccessorValueReader<?> reader = factory.valueReader(field);

        writer.set(bean, 3.14);
        assertEquals(3.14, reader.get(bean));
    }

    @Test
    void testFloatFieldAccess() throws Exception {
        PrimitiveFieldBean bean = new PrimitiveFieldBean();
        Field field = PrimitiveFieldBean.class.getDeclaredField("floatField");
        field.setAccessible(true);

        HibernateAccessorValueWriter writer = factory.valueWriter(field);
        HibernateAccessorValueReader<?> reader = factory.valueReader(field);

        writer.set(bean, 2.5f);
        assertEquals(2.5f, reader.get(bean));
    }

    @Test
    void testShortFieldAccess() throws Exception {
        PrimitiveFieldBean bean = new PrimitiveFieldBean();
        Field field = PrimitiveFieldBean.class.getDeclaredField("shortField");
        field.setAccessible(true);

        HibernateAccessorValueWriter writer = factory.valueWriter(field);
        HibernateAccessorValueReader<?> reader = factory.valueReader(field);

        writer.set(bean, (short) 7);
        assertEquals((short) 7, reader.get(bean));
    }

    @Test
    void testByteFieldAccess() throws Exception {
        PrimitiveFieldBean bean = new PrimitiveFieldBean();
        Field field = PrimitiveFieldBean.class.getDeclaredField("byteField");
        field.setAccessible(true);

        HibernateAccessorValueWriter writer = factory.valueWriter(field);
        HibernateAccessorValueReader<?> reader = factory.valueReader(field);

        writer.set(bean, (byte) 3);
        assertEquals((byte) 3, reader.get(bean));
    }

    @Test
    void testCharFieldAccess() throws Exception {
        PrimitiveFieldBean bean = new PrimitiveFieldBean();
        Field field = PrimitiveFieldBean.class.getDeclaredField("charField");
        field.setAccessible(true);

        HibernateAccessorValueWriter writer = factory.valueWriter(field);
        HibernateAccessorValueReader<?> reader = factory.valueReader(field);

        writer.set(bean, 'Z');
        assertEquals('Z', reader.get(bean));
    }

    @Test
    void testIntMethodAccess() throws Exception {
        PrimitiveFieldBean bean = new PrimitiveFieldBean();
        Method setter = PrimitiveFieldBean.class.getDeclaredMethod("setIntField", int.class);
        Method getter = PrimitiveFieldBean.class.getDeclaredMethod("getIntField");

        HibernateAccessorValueWriter writer = factory.valueWriter(setter);
        HibernateAccessorValueReader<?> reader = factory.valueReader(getter);

        writer.set(bean, 99);
        assertEquals(99, reader.get(bean));
    }

    @Test
    void testLongMethodAccess() throws Exception {
        PrimitiveFieldBean bean = new PrimitiveFieldBean();
        Method setter = PrimitiveFieldBean.class.getDeclaredMethod("setLongField", long.class);
        Method getter = PrimitiveFieldBean.class.getDeclaredMethod("getLongField");

        HibernateAccessorValueWriter writer = factory.valueWriter(setter);
        HibernateAccessorValueReader<?> reader = factory.valueReader(getter);

        writer.set(bean, 987654321L);
        assertEquals(987654321L, reader.get(bean));
    }

    @Test
    void testBooleanMethodAccess() throws Exception {
        PrimitiveFieldBean bean = new PrimitiveFieldBean();
        Method setter = PrimitiveFieldBean.class.getDeclaredMethod("setBooleanField", boolean.class);
        Method getter = PrimitiveFieldBean.class.getDeclaredMethod("isBooleanField");

        HibernateAccessorValueWriter writer = factory.valueWriter(setter);
        HibernateAccessorValueReader<?> reader = factory.valueReader(getter);

        writer.set(bean, true);
        assertEquals(true, reader.get(bean));
    }

    @Test
    void testDoubleMethodAccess() throws Exception {
        PrimitiveFieldBean bean = new PrimitiveFieldBean();
        Method setter = PrimitiveFieldBean.class.getDeclaredMethod("setDoubleField", double.class);
        Method getter = PrimitiveFieldBean.class.getDeclaredMethod("getDoubleField");

        HibernateAccessorValueWriter writer = factory.valueWriter(setter);
        HibernateAccessorValueReader<?> reader = factory.valueReader(getter);

        writer.set(bean, 2.718);
        assertEquals(2.718, reader.get(bean));
    }

    @Test
    void testFloatMethodAccess() throws Exception {
        PrimitiveFieldBean bean = new PrimitiveFieldBean();
        Method setter = PrimitiveFieldBean.class.getDeclaredMethod("setFloatField", float.class);
        Method getter = PrimitiveFieldBean.class.getDeclaredMethod("getFloatField");

        HibernateAccessorValueWriter writer = factory.valueWriter(setter);
        HibernateAccessorValueReader<?> reader = factory.valueReader(getter);

        writer.set(bean, 1.5f);
        assertEquals(1.5f, reader.get(bean));
    }

    @Test
    void testShortMethodAccess() throws Exception {
        PrimitiveFieldBean bean = new PrimitiveFieldBean();
        Method setter = PrimitiveFieldBean.class.getDeclaredMethod("setShortField", short.class);
        Method getter = PrimitiveFieldBean.class.getDeclaredMethod("getShortField");

        HibernateAccessorValueWriter writer = factory.valueWriter(setter);
        HibernateAccessorValueReader<?> reader = factory.valueReader(getter);

        writer.set(bean, (short) 11);
        assertEquals((short) 11, reader.get(bean));
    }

    @Test
    void testByteMethodAccess() throws Exception {
        PrimitiveFieldBean bean = new PrimitiveFieldBean();
        Method setter = PrimitiveFieldBean.class.getDeclaredMethod("setByteField", byte.class);
        Method getter = PrimitiveFieldBean.class.getDeclaredMethod("getByteField");

        HibernateAccessorValueWriter writer = factory.valueWriter(setter);
        HibernateAccessorValueReader<?> reader = factory.valueReader(getter);

        writer.set(bean, (byte) 5);
        assertEquals((byte) 5, reader.get(bean));
    }

    @Test
    void testCharMethodAccess() throws Exception {
        PrimitiveFieldBean bean = new PrimitiveFieldBean();
        Method setter = PrimitiveFieldBean.class.getDeclaredMethod("setCharField", char.class);
        Method getter = PrimitiveFieldBean.class.getDeclaredMethod("getCharField");

        HibernateAccessorValueWriter writer = factory.valueWriter(setter);
        HibernateAccessorValueReader<?> reader = factory.valueReader(getter);

        writer.set(bean, 'A');
        assertEquals('A', reader.get(bean));
    }

    // Widening: a smaller primitive value written to a wider primitive target must be
    // accepted, mirroring the reflection semantics (JLS 5.1.2 widening conversions).

    @Test
    @DisplayName("int value widened into a long field")
    void testIntWidenedToLongField() throws Exception {
        PrimitiveFieldBean bean = new PrimitiveFieldBean();
        Field field = PrimitiveFieldBean.class.getDeclaredField("longField");
        field.setAccessible(true);

        HibernateAccessorValueWriter writer = factory.valueWriter(field);
        HibernateAccessorValueReader<?> reader = factory.valueReader(field);

        writer.set(bean, 42); // Integer -> long
        assertEquals(42L, reader.get(bean));
    }

    @Test
    @DisplayName("int value widened into a long setter")
    void testIntWidenedToLongMethod() throws Exception {
        PrimitiveFieldBean bean = new PrimitiveFieldBean();
        Method setter = PrimitiveFieldBean.class.getDeclaredMethod("setLongField", long.class);
        Method getter = PrimitiveFieldBean.class.getDeclaredMethod("getLongField");

        HibernateAccessorValueWriter writer = factory.valueWriter(setter);
        HibernateAccessorValueReader<?> reader = factory.valueReader(getter);

        writer.set(bean, 99); // Integer -> long
        assertEquals(99L, reader.get(bean));
    }

    @Test
    @DisplayName("byte value widened into an int field")
    void testByteWidenedToIntField() throws Exception {
        PrimitiveFieldBean bean = new PrimitiveFieldBean();
        Field field = PrimitiveFieldBean.class.getDeclaredField("intField");
        field.setAccessible(true);

        HibernateAccessorValueWriter writer = factory.valueWriter(field);
        HibernateAccessorValueReader<?> reader = factory.valueReader(field);

        writer.set(bean, (byte) 5); // Byte -> int
        assertEquals(5, reader.get(bean));
    }

    @Test
    @DisplayName("char value widened into an int field")
    void testCharWidenedToIntField() throws Exception {
        PrimitiveFieldBean bean = new PrimitiveFieldBean();
        Field field = PrimitiveFieldBean.class.getDeclaredField("intField");
        field.setAccessible(true);

        HibernateAccessorValueWriter writer = factory.valueWriter(field);
        HibernateAccessorValueReader<?> reader = factory.valueReader(field);

        writer.set(bean, 'A'); // Character -> int
        assertEquals((int) 'A', reader.get(bean));
    }

    @Test
    @DisplayName("short value widened into a long field")
    void testShortWidenedToLongField() throws Exception {
        PrimitiveFieldBean bean = new PrimitiveFieldBean();
        Field field = PrimitiveFieldBean.class.getDeclaredField("longField");
        field.setAccessible(true);

        HibernateAccessorValueWriter writer = factory.valueWriter(field);
        HibernateAccessorValueReader<?> reader = factory.valueReader(field);

        writer.set(bean, (short) 7); // Short -> long
        assertEquals(7L, reader.get(bean));
    }

    @Test
    @DisplayName("int value widened into a double field")
    void testIntWidenedToDoubleField() throws Exception {
        PrimitiveFieldBean bean = new PrimitiveFieldBean();
        Field field = PrimitiveFieldBean.class.getDeclaredField("doubleField");
        field.setAccessible(true);

        HibernateAccessorValueWriter writer = factory.valueWriter(field);
        HibernateAccessorValueReader<?> reader = factory.valueReader(field);

        writer.set(bean, 3); // Integer -> double
        assertEquals(3.0, reader.get(bean));
    }

    @Test
    @DisplayName("float value widened into a double field")
    void testFloatWidenedToDoubleField() throws Exception {
        PrimitiveFieldBean bean = new PrimitiveFieldBean();
        Field field = PrimitiveFieldBean.class.getDeclaredField("doubleField");
        field.setAccessible(true);

        HibernateAccessorValueWriter writer = factory.valueWriter(field);
        HibernateAccessorValueReader<?> reader = factory.valueReader(field);

        writer.set(bean, 2.5f); // Float -> double
        assertEquals(2.5, reader.get(bean));
    }

    // Narrowing: a wider primitive value written to a narrower primitive target must be
    // rejected, again mirroring reflection (which throws IllegalArgumentException).

    @Test
    @DisplayName("long value narrowed into an int field is rejected")
    void testLongNarrowedToIntFieldRejected() throws Exception {
        PrimitiveFieldBean bean = new PrimitiveFieldBean();
        Field field = PrimitiveFieldBean.class.getDeclaredField("intField");
        field.setAccessible(true);

        HibernateAccessorValueWriter writer = factory.valueWriter(field);

        assertThrows(RuntimeException.class, () -> writer.set(bean, 42L)); // Long -> int
    }

    @Test
    @DisplayName("long value narrowed into an int setter is rejected")
    void testLongNarrowedToIntMethodRejected() throws Exception {
        PrimitiveFieldBean bean = new PrimitiveFieldBean();
        Method setter = PrimitiveFieldBean.class.getDeclaredMethod("setIntField", int.class);

        HibernateAccessorValueWriter writer = factory.valueWriter(setter);

        assertThrows(RuntimeException.class, () -> writer.set(bean, 42L)); // Long -> int
    }
}
