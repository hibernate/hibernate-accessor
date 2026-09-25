/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.tck.tests.primitive;

import org.hibernate.accessor.tck.tests.beans.PrimitiveFieldBean;
import org.hibernate.accessor.tck.util.TckHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PrimitiveConversionMatrixTest {
	@Test
	void singleAndMultiWritersMatchReflectionForEveryPrimitivePair() throws Exception {
		var factory = TckHelper.factory();
		Object[] sources = { true, (byte) 7, (short) 7, 'A', 7, 7L, 7.5f, 7.5, null };
		for ( var field : PrimitiveFieldBean.class.getDeclaredFields() ) {
			field.setAccessible( true );
			String suffix = Character.toUpperCase( field.getName().charAt( 0 ) ) + field.getName().substring( 1 );
			var setter = PrimitiveFieldBean.class.getMethod( "set" + suffix, field.getType() );
			var fieldWriter = factory.valueWriter( field );
			var methodWriter = factory.valueWriter( setter );
			var multiFieldWriter = factory.multiValueWriter( PrimitiveFieldBean.class, field );
			var multiMethodWriter = factory.multiValueWriter( PrimitiveFieldBean.class, setter );
			String strategy = factory.getClass().getPackageName();
			if ( strategy.contains( ".asm." ) || strategy.contains( ".bytebuddy." ) || strategy.contains( ".classfile." ) ) {
				org.junit.jupiter.api.Assertions.assertTrue( multiFieldWriter.getClass().isHidden(), "must exercise generated field conversion" );
				org.junit.jupiter.api.Assertions.assertTrue( multiMethodWriter.getClass().isHidden(), "must exercise generated method conversion" );
			}
			for ( Object source : sources ) {
				var expected = new PrimitiveFieldBean();
				boolean accepted;
				try { field.set( expected, source ); accepted = true; }
				catch (IllegalArgumentException e) { accepted = false; }
				var bean = new PrimitiveFieldBean();
				if ( accepted ) {
					fieldWriter.set( bean, source );
					assertEquals( field.get( expected ), field.get( bean ) );
					methodWriter.set( bean, source );
					assertEquals( field.get( expected ), field.get( bean ) );
					multiFieldWriter.set( bean, new Object[] { source } );
					assertEquals( field.get( expected ), field.get( bean ) );
					multiMethodWriter.set( bean, new Object[] { source } );
					assertEquals( field.get( expected ), field.get( bean ) );
				}
				else {
					assertThrows( RuntimeException.class, () -> fieldWriter.set( bean, source ) );
					assertThrows( RuntimeException.class, () -> methodWriter.set( bean, source ) );
					assertThrows( RuntimeException.class, () -> multiFieldWriter.set( bean, new Object[] { source } ) );
					assertThrows( RuntimeException.class, () -> multiMethodWriter.set( bean, new Object[] { source } ) );
				}
			}
		}
	}
}
