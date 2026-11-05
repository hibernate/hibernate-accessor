package org.hibernate.accessor.tck.tests.interfacemethod;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.hibernate.accessor.tck.util.TckHelper;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class DefaultInterfaceMethodTest {
	public interface Property {
		String readStorage();

		void writeStorage(String value);

		default String getValue() {
			return "read:" + readStorage();
		}

		default void setValue(String value) {
			writeStorage( "written:" + value );
		}
	}

	public static class Bean implements Property {
		public String storage;

		@Override
		public String readStorage() {
			return storage;
		}

		@Override
		public void writeStorage(String value) {
			storage = value;
		}
	}

	@Test
	void inheritedDefaultMethodsInvokeTheirBodies() throws Exception {
		var factory = TckHelper.factory();
		var bean = new Bean();
		factory.valueWriter( Property.class.getMethod( "setValue", String.class ) ).set( bean, "single" );
		assertEquals( "written:single", bean.storage );
		bean.storage = "independent";
		assertEquals( "read:independent", factory.valueReader( Property.class.getMethod( "getValue" ) ).get( bean ) );
	}

	@Test
	@Tag("multi-accessor")
	void inheritedDefaultMethodsWorkInMultiValueAccess() throws Exception {
		var factory = TckHelper.factory();
		var bean = new Bean();
		factory.multiValueWriter( Bean.class, Property.class.getMethod( "setValue", String.class ) )
				.set( bean, new Object[] { "multi" } );
		assertEquals( "written:multi", bean.storage );
		bean.storage = "independent";
		assertArrayEquals( new Object[] { "read:independent" },
				factory.multiValueReader( Bean.class, Property.class.getMethod( "getValue" ) ).get( bean ) );
	}
}
