/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.tck.tests.nested;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.Instantiator;
import org.hibernate.accessor.MultiValueReader;
import org.hibernate.accessor.MultiValueWriter;
import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.tck.tests.beans.nested.NestedClassBean;
import org.hibernate.accessor.tck.util.TckHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Access nested classes with different visibility levels")
public class NestedClassAccessTest {

	private AccessorFactory factory;

	@BeforeAll
	void setup() {
		factory = TckHelper.factory();
	}

	static Stream<Arguments> nestedClasses() {
		return Stream.of(
				Arguments.of( "public", NestedClassBean.PublicStaticNested.class, new NestedClassBean.PublicStaticNested() ),
				Arguments.of( "protected", NestedClassBean.protectedNestedClass(), NestedClassBean.createProtectedNested() ),
				Arguments.of( "default", NestedClassBean.defaultNestedClass(), NestedClassBean.createDefaultNested() ),
				Arguments.of( "private", NestedClassBean.privateNestedClass(), NestedClassBean.createPrivateNested() )
		);
	}

	@ParameterizedTest(name = "{0} static nested class")
	@MethodSource("nestedClasses")
	@DisplayName("Instantiate nested class")
	void testInstantiation(String visibility, Class<?> nestedClass, Object ignored) throws Exception {
		Constructor<?> constructor = nestedClass.getDeclaredConstructor();
		constructor.setAccessible( true );
		Instantiator<?> instantiator = factory.instantiator( constructor );

		Object instance = instantiator.create();
		assertNotNull( instance );
		assertEquals( nestedClass, instance.getClass() );
	}

	@ParameterizedTest(name = "{0} static nested class — public field")
	@MethodSource("nestedClasses")
	@DisplayName("Access public field on nested class")
	void testPublicFieldAccess(String visibility, Class<?> nestedClass, Object instance) throws Exception {
		Field field = nestedClass.getDeclaredField( "publicField" );
		ValueWriter writer = factory.valueWriter( field );
		ValueReader<?> reader = factory.valueReader( field );

		writer.set( instance, "hello" );
		assertEquals( "hello", reader.get( instance ) );
	}

	@ParameterizedTest(name = "{0} static nested class — private field")
	@MethodSource("nestedClasses")
	@DisplayName("Access private field on nested class")
	void testPrivateFieldAccess(String visibility, Class<?> nestedClass, Object instance) throws Exception {
		Field field = nestedClass.getDeclaredField( "privateField" );
		ValueWriter writer = factory.valueWriter( field );
		ValueReader<?> reader = factory.valueReader( field );

		writer.set( instance, "secret" );
		assertEquals( "secret", reader.get( instance ) );
	}

	@ParameterizedTest(name = "{0} static nested class — public method")
	@MethodSource("nestedClasses")
	@DisplayName("Access public getter/setter on nested class")
	void testPublicMethodAccess(String visibility, Class<?> nestedClass, Object instance) throws Exception {
		Method setter = nestedClass.getDeclaredMethod( "setPublicField", String.class );
		Method getter = nestedClass.getDeclaredMethod( "getPublicField" );

		ValueWriter writer = factory.valueWriter( setter );
		ValueReader<?> reader = factory.valueReader( getter );

		writer.set( instance, "via-method" );
		assertEquals( "via-method", reader.get( instance ) );
	}

	@ParameterizedTest(name = "{0} static nested class — private method")
	@MethodSource("nestedClasses")
	@DisplayName("Access private getter/setter on nested class")
	void testPrivateMethodAccess(String visibility, Class<?> nestedClass, Object instance) throws Exception {
		Method setter = nestedClass.getDeclaredMethod( "setPrivateField", String.class );
		Method getter = nestedClass.getDeclaredMethod( "getPrivateField" );

		ValueWriter writer = factory.valueWriter( setter );
		ValueReader<?> reader = factory.valueReader( getter );

		writer.set( instance, "via-private-method" );
		assertEquals( "via-private-method", reader.get( instance ) );
	}

	@ParameterizedTest(name = "{0} static nested class — multi-value field access")
	@MethodSource("nestedClasses")
	@DisplayName("Multi-value read/write on nested class fields")
	void testMultiValueFieldAccess(String visibility, Class<?> nestedClass, Object instance) throws Exception {
		Field publicField = nestedClass.getDeclaredField( "publicField" );
		Field privateField = nestedClass.getDeclaredField( "privateField" );

		MultiValueWriter writer = factory.multiValueWriter( nestedClass, publicField, privateField );
		MultiValueReader reader = factory.multiValueReader( nestedClass, publicField, privateField );

		writer.set( instance, new Object[]{ "pub-val", "priv-val" } );
		Object[] values = reader.get( instance );

		assertEquals( 2, values.length );
		assertEquals( "pub-val", values[0] );
		assertEquals( "priv-val", values[1] );
	}

	@ParameterizedTest(name = "{0} static nested class — multi-value mixed access")
	@MethodSource("nestedClasses")
	@DisplayName("Multi-value read/write with mixed fields and methods on nested class")
	void testMultiValueMixedAccess(String visibility, Class<?> nestedClass, Object instance) throws Exception {
		Field publicField = nestedClass.getDeclaredField( "publicField" );
		Method getPrivate = nestedClass.getDeclaredMethod( "getPrivateField" );
		Method setPrivate = nestedClass.getDeclaredMethod( "setPrivateField", String.class );

		MultiValueWriter writer = factory.multiValueWriter( nestedClass, publicField, setPrivate );
		MultiValueReader reader = factory.multiValueReader( nestedClass, publicField, getPrivate );

		writer.set( instance, new Object[]{ "field-val", "method-val" } );
		Object[] values = reader.get( instance );

		assertEquals( 2, values.length );
		assertEquals( "field-val", values[0] );
		assertEquals( "method-val", values[1] );
	}
}
