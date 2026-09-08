package org.hibernate.accessor.tck.tests.interfacemethod;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.tck.util.TckHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Interface method access")
public class InterfaceMethodAccessTest {

    private AccessorFactory factory;

    @BeforeAll
    void setup() {
        factory = TckHelper.factory();
    }

    @Test
    @DisplayName("Read and write via methods declared on an interface")
    @SuppressWarnings("unchecked")
    void testInterfaceMethodAccess() throws Exception {
        Method getter = GreetingService.class.getDeclaredMethod("getGreeting");
        Method setter = GreetingService.class.getDeclaredMethod("setGreeting", String.class);

        ValueReader<String> reader =
                (ValueReader<String>) factory.valueReader(getter);
        ValueWriter writer = factory.valueWriter(setter);

        GreetingServiceImpl instance = new GreetingServiceImpl();

        assertNull(reader.get(instance));

        writer.set(instance, "Hello");
        assertEquals("Hello", reader.get(instance));

        writer.set(instance, "World");
        assertEquals("World", reader.get(instance));
    }
}
