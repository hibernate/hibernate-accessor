/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.tck.tests.varargs;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Method;
import java.util.Set;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.MultiValueWriter;
import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.tck.tests.beans.VarargsBean;
import org.hibernate.accessor.tck.util.IsolatingClassLoader;
import org.hibernate.accessor.tck.util.TckHelper;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class VarargsSetterTest {
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void singleValuePreservesArray(boolean isolated) throws Exception {
		Class<?> beanClass = beanClass( isolated );
		AccessorFactory factory = TckHelper.factory();
		Object bean = beanClass.getConstructor().newInstance();
		String[] fields = { "strings", "objects", "ints" };
		Method[] setters = setters( beanClass );
		for ( int i = 0; i < setters.length; i++ ) {
			ValueWriter writer = factory.valueWriter( setters[i] );
			for ( Object[] values : values() ) {
				writer.set( bean, values[i] );
				assertSame( values[i], beanClass.getField( fields[i] ).get( bean ), fields[i] );
			}
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void multiValuePreservesArrays(boolean isolated) throws Exception {
		Class<?> beanClass = beanClass( isolated );
		Object bean = beanClass.getConstructor().newInstance();
		MultiValueWriter writer = TckHelper.factory().multiValueWriter( beanClass, setters( beanClass ) );
		for ( Object[] values : values() ) {
			writer.set( bean, values );
			assertSame( values[0], beanClass.getField( "strings" ).get( bean ) );
			assertSame( values[1], beanClass.getField( "objects" ).get( bean ) );
			assertSame( values[2], beanClass.getField( "ints" ).get( bean ) );
		}
	}

	private static Object[][] values() {
		return new Object[][] {
				{ new String[] { "a", "b" }, new Object[] { "a", 42 }, new int[] { 1, 2 } },
				{ new String[0], new Object[0], new int[0] },
				{ null, null, null }
		};
	}

	private static Method[] setters(Class<?> beanClass) throws Exception {
		return new Method[] {
				beanClass.getMethod( "setStrings", String[].class ),
				beanClass.getMethod( "setObjects", Object[].class ),
				beanClass.getMethod( "setInts", int[].class )
		};
	}

	private static Class<?> beanClass(boolean isolated) throws Exception {
		if ( !isolated ) {
			return VarargsBean.class;
		}
		Class<?> beanClass = new IsolatingClassLoader(
				Set.of( VarargsBean.class.getName() ), VarargsBean.class.getClassLoader()
		).loadClass( VarargsBean.class.getName() );
		assertNotSame( VarargsBean.class.getModule(), beanClass.getModule() );
		return beanClass;
	}
}
