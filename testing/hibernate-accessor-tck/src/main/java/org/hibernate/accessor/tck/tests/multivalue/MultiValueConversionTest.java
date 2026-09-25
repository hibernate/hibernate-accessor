/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.tck.tests.multivalue;

import org.hibernate.accessor.MultiValueAccessorGenerationException;
import org.hibernate.accessor.tck.util.TckHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MultiValueConversionTest {
	public static class Bean {
		public long value;
		public double other;
		public final String fixed = new String( "before" );
		public long setValue(long value) { this.value = value; return value; }
		public double setOther(double value) { this.other = value; return value; }
	}
	public static class Child extends Bean { }

	@Test
	void directFieldsAcceptWidening() throws Exception {
		var factory = TckHelper.factory();
		var bean = new Bean();
		factory.multiValueWriter( Bean.class, Bean.class.getField( "value" ), Bean.class.getField( "other" ) )
				.set( bean, new Object[] { 42, 2.5f } );
		assertEquals( 42L, bean.value );
		assertEquals( 2.5, bean.other );
	}

	@Test
	void directMethodsAcceptWideningAndDiscardWideReturns() throws Exception {
		var factory = TckHelper.factory();
		var bean = new Bean();
		factory.multiValueWriter( Bean.class, Bean.class.getMethod( "setValue", long.class ),
				Bean.class.getMethod( "setOther", double.class ) ).set( bean, new Object[] { 'A', 3L } );
		assertEquals( 65L, bean.value );
		assertEquals( 3.0, bean.other );
	}

	@Test
	void directFieldsRejectNarrowing() throws Exception {
		var writer = TckHelper.factory().multiValueWriter( Bean.class, Bean.class.getField( "value" ) );
		assertThrows( RuntimeException.class, () -> writer.set( new Bean(), new Object[] { 1.5 } ) );
	}

	@Test
	void generatedMultiWritersRejectFinalFields() throws Exception {
		var factory = TckHelper.factory();
		String strategy = factory.getClass().getPackageName();
		if ( !strategy.contains( ".asm." ) && !strategy.contains( ".bytebuddy." ) && !strategy.contains( ".classfile." ) ) {
			return;
		}
		var field = Bean.class.getField( "fixed" );
		for ( Class<?> type : new Class<?>[] { Bean.class, Child.class } ) {
			assertThrows( MultiValueAccessorGenerationException.class,
					() -> factory.multiValueWriter( type, field ) );
		}
	}
}
