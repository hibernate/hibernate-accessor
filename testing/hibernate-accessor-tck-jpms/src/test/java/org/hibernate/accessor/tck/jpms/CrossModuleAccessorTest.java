/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.tck.jpms;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.Instantiator;
import org.hibernate.accessor.MultiValueReader;
import org.hibernate.accessor.MultiValueWriter;
import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.asm.AsmAccessorFactory;
import org.hibernate.accessor.bytebuddy.ByteBuddyAccessorFactory;
import org.hibernate.accessor.tck.jpms.entities.SimpleEntity;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CrossModuleAccessorTest {

	static Stream<AccessorFactory> factories() {
		MethodHandles.Lookup lookup = MethodHandles.lookup();
		return Stream.of(
				AccessorFactory.reflection(),
				AccessorFactory.lambda( lookup ),
				AsmAccessorFactory.factory( lookup ),
				ByteBuddyAccessorFactory.factory( lookup )
		);
	}

	@org.junit.jupiter.api.Test
	void entityClassIsInNamedModule() {
		assertThat( SimpleEntity.class.getModule().isNamed() )
				.as( "SimpleEntity must be in a named JPMS module" )
				.isTrue();
		assertThat( SimpleEntity.class.getModule().getName() )
				.isEqualTo( "org.hibernate.accessor.tck.jpms.entities" );
	}

	@ParameterizedTest
	@MethodSource("factories")
	void testFieldAccess(AccessorFactory factory) throws Exception {
		Field nameField = SimpleEntity.class.getDeclaredField( "name" );
		nameField.setAccessible( true );

		ValueReader<?> reader = factory.valueReader( nameField );
		ValueWriter writer = factory.valueWriter( nameField );

		SimpleEntity entity = new SimpleEntity( 1, "original" );
		assertThat( reader.get( entity ) ).isEqualTo( "original" );

		writer.set( entity, "updated" );
		assertThat( reader.get( entity ) ).isEqualTo( "updated" );
	}

	@ParameterizedTest
	@MethodSource("factories")
	void testMethodAccess(AccessorFactory factory) throws Exception {
		Method getter = SimpleEntity.class.getDeclaredMethod( "getName" );
		Method setter = SimpleEntity.class.getDeclaredMethod( "setName", String.class );

		ValueReader<?> reader = factory.valueReader( getter );
		ValueWriter writer = factory.valueWriter( setter );

		SimpleEntity entity = new SimpleEntity( 1, "original" );
		assertThat( reader.get( entity ) ).isEqualTo( "original" );

		writer.set( entity, "updated" );
		assertThat( reader.get( entity ) ).isEqualTo( "updated" );
	}

	@ParameterizedTest
	@MethodSource("factories")
	void testInstantiator(AccessorFactory factory) throws Exception {
		Instantiator<SimpleEntity> instantiator =
				factory.instantiator( SimpleEntity.class.getDeclaredConstructor() );

		SimpleEntity entity = instantiator.create();
		assertThat( entity ).isNotNull();
	}

	@ParameterizedTest
	@MethodSource("factories")
	void testMultiValueAccess(AccessorFactory factory) throws Exception {
		Field idField = SimpleEntity.class.getDeclaredField( "id" );
		idField.setAccessible( true );
		Field nameField = SimpleEntity.class.getDeclaredField( "name" );
		nameField.setAccessible( true );

		MultiValueReader reader = factory.multiValueReader(
				SimpleEntity.class, idField, nameField
		);
		MultiValueWriter writer = factory.multiValueWriter(
				SimpleEntity.class, idField, nameField
		);

		SimpleEntity entity = new SimpleEntity( 1, "original" );
		Object[] values = reader.get( entity );
		assertThat( values ).containsExactly( 1, "original" );

		writer.set( entity, new Object[]{ 2, "updated" } );
		values = reader.get( entity );
		assertThat( values ).containsExactly( 2, "updated" );
	}
}
