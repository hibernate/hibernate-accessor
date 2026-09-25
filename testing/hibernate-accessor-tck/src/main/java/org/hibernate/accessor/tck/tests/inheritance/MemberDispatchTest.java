/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.tck.tests.inheritance;

import org.hibernate.accessor.tck.util.TckHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MemberDispatchTest {
	public static class Parent {
		public String value;

		public void setValue(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}
	}

	public static class Child extends Parent {
		public String value;

		@Override
		public void setValue(String value) {
			super.setValue( "overridden:" + value );
		}

		@Override
		public String getValue() {
			return "overridden:" + super.getValue();
		}
	}

	@Test
	void shadowedFieldsRemainDistinctForSingleAndMultiValueAccess() throws Exception {
		var factory = TckHelper.factory();
		var parentField = Parent.class.getField( "value" );
		var childField = Child.class.getDeclaredField( "value" );
		var child = new Child();

		factory.valueWriter( parentField ).set( child, "parent" );
		factory.valueWriter( childField ).set( child, "child" );
		assertEquals( "parent", factory.valueReader( parentField ).get( child ) );
		assertEquals( "child", factory.valueReader( childField ).get( child ) );

		factory.multiValueWriter( Child.class, childField, parentField )
				.set( child, new Object[] { "second child", "second parent" } );
		assertArrayEquals( new Object[] { "second parent", "second child" },
				factory.multiValueReader( Child.class, parentField, childField ).get( child ) );
	}

	@Test
	void parentMethodAccessorsDispatchToChildOverrides() throws Exception {
		var factory = TckHelper.factory();
		var setter = Parent.class.getMethod( "setValue", String.class );
		var getter = Parent.class.getMethod( "getValue" );
		var child = new Child();

		factory.valueWriter( setter ).set( child, "single" );
		assertEquals( "overridden:single", ( (Parent) child ).value );
		assertEquals( "overridden:overridden:single", factory.valueReader( getter ).get( child ) );

		factory.multiValueWriter( Child.class, setter ).set( child, new Object[] { "multi" } );
		assertArrayEquals( new Object[] { "overridden:overridden:multi" },
				factory.multiValueReader( Child.class, getter ).get( child ) );
	}
}
