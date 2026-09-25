/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.tck.tests.instantiation;

import java.util.Set;

import org.hibernate.accessor.tck.util.IsolatingClassLoader;
import org.hibernate.accessor.tck.util.TckHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class InvocationShapeTest {
	public static class Bean {
		public String[] values;
		public Bean() { }
		public Bean(String... values) { this.values = values; }
		public void setValues(String... values) { this.values = values; }
	}

	@Test
	void constructorsRejectExtraArguments() throws Exception {
		var factory = TckHelper.factory();
		var noArgs = factory.instantiator( Bean.class.getConstructor() );
		assertThrows( RuntimeException.class, () -> noArgs.create( "extra" ) );
		var oneArg = factory.instantiator( Bean.class.getConstructor( String[].class ) );
		assertThrows( RuntimeException.class, () -> oneArg.create( new String[] { "a" }, "extra" ) );
	}

	@Test
	void varargsMembersAcceptTheDeclaredArrayAsOneArgument() throws Exception {
		var factory = TckHelper.factory();
		var loader = new IsolatingClassLoader( Set.of( Bean.class.getName() ), Bean.class.getClassLoader() );
		for ( var type : new Class<?>[] { Bean.class, loader.loadClass( Bean.class.getName() ) } ) {
			String[] values = { "a", "b" };
			var bean = factory.instantiator( type.getConstructor( String[].class ) ).create( (Object) values );
			assertArrayEquals( values, (String[]) type.getField( "values" ).get( bean ) );
			values = new String[] { "c", "d" };
			factory.valueWriter( type.getMethod( "setValues", String[].class ) ).set( bean, values );
			assertArrayEquals( values, (String[]) type.getField( "values" ).get( bean ) );
		}
	}
}
