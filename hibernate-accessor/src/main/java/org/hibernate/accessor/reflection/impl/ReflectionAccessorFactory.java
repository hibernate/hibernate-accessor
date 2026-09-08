/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.reflection.impl;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.Instantiator;
import org.hibernate.accessor.MultiValueReader;
import org.hibernate.accessor.MultiValueWriter;
import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.spi.MemberValidation;

public class ReflectionAccessorFactory implements AccessorFactory {

	public static final ReflectionAccessorFactory INSTANCE = new ReflectionAccessorFactory();

	private ReflectionAccessorFactory() {
	}

	@Override
	public <T> Instantiator<T> instantiator(Constructor<T> constructor) {
		return new ReflectionConstructorInstantiator<>( constructor );
	}

	@Override
	public ValueReader<?> valueReader(Field field) {
		MemberValidation.validateInstanceMember( field );
		return new ReflectionFieldValueReader<>( field );
	}

	@Override
	public ValueReader<?> valueReader(Method method) {
		MemberValidation.validateReaderMethod( method );
		return new ReflectionMethodValueReader<>( method );
	}

	@Override
	public ValueWriter valueWriter(Field field) {
		MemberValidation.validateInstanceMember( field );
		return new ReflectionFieldValueWriter( field );
	}

	@Override
	public ValueWriter valueWriter(Method setter) {
		MemberValidation.validateWriterMethod( setter );
		return new ReflectionMethodValueWriter( setter );
	}

	@Override
	public MultiValueReader multiValueReader(Class<?> declaringClass, Member... members) {
		if ( members.length == 0 ) {
			throw new IllegalArgumentException( "At least one member is required" );
		}
		final ValueReader<?>[] readers = new ValueReader<?>[members.length];
		for ( int i = 0; i < members.length; i++ ) {
			final Member member = members[i];
			MemberValidation.validateMemberDeclaringType( declaringClass, member );
			MemberValidation.validateReaderMember( member );
			if ( member instanceof Field field ) {
				readers[i] = valueReader( field );
			}
			else if ( member instanceof Method method ) {
				readers[i] = valueReader( method );
			}
			else {
				throw new IllegalArgumentException( "Unsupported member type: " + member.getClass().getName() );
			}
		}
		return new ReflectionMultiValueReader( readers );
	}

	@Override
	public MultiValueWriter multiValueWriter(Class<?> declaringClass, Member... members) {
		if ( members.length == 0 ) {
			throw new IllegalArgumentException( "At least one member is required" );
		}
		final ValueWriter[] writers = new ValueWriter[members.length];
		for ( int i = 0; i < members.length; i++ ) {
			final Member member = members[i];
			MemberValidation.validateMemberDeclaringType( declaringClass, member );
			MemberValidation.validateWriterMember( member );
			if ( member instanceof Field field ) {
				writers[i] = valueWriter( field );
			}
			else if ( member instanceof Method method ) {
				writers[i] = valueWriter( method );
			}
			else {
				throw new IllegalArgumentException( "Unsupported member type: " + member.getClass().getName() );
			}
		}
		return new ReflectionMultiValueWriter( writers );
	}

}
