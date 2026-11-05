package org.hibernate.accessor.tck.tests.interfacemethod;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.hibernate.accessor.tck.util.TckHelper;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class GenericBridgeMethodTest {
	public interface Property<T> {
		T getValue();

		void setValue(T value);
	}

	public static class Bean implements Property<String> {
		public String value;

		@Override
		public String getValue() {
			return "read:" + value;
		}

		@Override
		public void setValue(String value) {
			this.value = "written:" + value;
		}
	}

	private static Method bridge(String name) {
		return Arrays.stream( Bean.class.getDeclaredMethods() )
				.filter( method -> method.isBridge() && method.getName().equals( name ) )
				.findFirst().orElseThrow();
	}

	@Test
	void compilerGeneratedBridgesInvokeTypedAccessors() {
		var factory = TckHelper.factory();
		var bean = new Bean();
		factory.valueWriter( bridge( "setValue" ) ).set( bean, "single" );
		assertEquals( "written:single", bean.value );
		bean.value = "independent";
		assertEquals( "read:independent", factory.valueReader( bridge( "getValue" ) ).get( bean ) );
	}

	@Test
	@Tag("multi-accessor")
	void compilerGeneratedBridgesWorkInMultiValueAccess() {
		var factory = TckHelper.factory();
		var bean = new Bean();
		factory.multiValueWriter( Bean.class, bridge( "setValue" ) ).set( bean, new Object[] { "multi" } );
		assertEquals( "written:multi", bean.value );
		bean.value = "independent";
		assertArrayEquals( new Object[] { "read:independent" },
				factory.multiValueReader( Bean.class, bridge( "getValue" ) ).get( bean ) );
	}
}
