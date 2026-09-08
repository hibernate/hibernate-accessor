/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.methodhandle.impl;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.Instantiator;
import org.hibernate.accessor.MultiValueReader;
import org.hibernate.accessor.MultiValueWriter;
import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.spi.AccessorConfiguration;
import org.hibernate.accessor.spi.MemberValidation;

import org.jboss.logging.Logger;

public class MethodHandleAccessorFactory implements AccessorFactory {

	private static final Logger LOG = Logger.getLogger( MethodHandleAccessorFactory.class );

	private final MethodHandles.Lookup lookup;
	private final AccessorFactory reflectionFallback = AccessorFactory.reflection();

	public MethodHandleAccessorFactory(MethodHandles.Lookup lookup) {
		this( new AccessorConfiguration( lookup ) );
	}

	public MethodHandleAccessorFactory(AccessorConfiguration configuration) {
		this.lookup = configuration.lookup();
	}

	@Override
	public <T> Instantiator<T> instantiator(Constructor<T> constructor) {
		try {
			return new MethodHandleInstantiator<>(
					privateLookup( constructor.getDeclaringClass() ).unreflectConstructor( constructor )
							.asSpreader( Object[].class, constructor.getParameterCount() ) );
		}
		catch (RuntimeException | IllegalAccessException e) {
			LOG.debugf( e, "Failed to create method-handle instantiator for %s, falling back to reflection", constructor );
			return reflectionFallback.instantiator( constructor );
		}
	}

	@Override
	public ValueReader<?> valueReader(Field field) {
		MemberValidation.validateInstanceMember( field );
		try {
			return new MethodHandleFieldValueReader<>(
					privateLookup( field.getDeclaringClass() ).unreflectGetter( field ) );
		}
		catch (RuntimeException | IllegalAccessException e) {
			LOG.debugf( e, "Failed to create method-handle field reader for %s, falling back to reflection", field );
			return reflectionFallback.valueReader( field );
		}
	}

	@Override
	public ValueReader<?> valueReader(Method method) {
		MemberValidation.validateReaderMethod( method );
		try {
			return new MethodHandleMethodValueReader<>(
					privateLookup( method.getDeclaringClass() ).unreflect( method ) );
		}
		catch (RuntimeException | IllegalAccessException e) {
			LOG.debugf( e, "Failed to create method-handle method reader for %s, falling back to reflection", method );
			return reflectionFallback.valueReader( method );
		}
	}

	@Override
	public ValueWriter valueWriter(Field field) {
		MemberValidation.validateInstanceMember( field );
		if ( Modifier.isFinal( field.getModifiers() ) ) {
			return reflectionFallback.valueWriter( field );
		}
		try {
			return new MethodHandleFieldValueWriter(
					privateLookup( field.getDeclaringClass() ).unreflectSetter( field ) );
		}
		catch (RuntimeException | IllegalAccessException e) {
			LOG.debugf( e, "Failed to create method-handle field writer for %s, falling back to reflection", field );
			return reflectionFallback.valueWriter( field );
		}
	}

	@Override
	public ValueWriter valueWriter(Method setter) {
		MemberValidation.validateWriterMethod( setter );
		try {
			return new MethodHandleMethodValueWriter(
					privateLookup( setter.getDeclaringClass() ).unreflect( setter ) );
		}
		catch (RuntimeException | IllegalAccessException e) {
			LOG.debugf( e, "Failed to create method-handle method writer for %s, falling back to reflection", setter );
			return reflectionFallback.valueWriter( setter );
		}
	}

	@Override
	public MultiValueReader multiValueReader(Class<?> declaringClass, Member... members) {
		if ( members.length == 0 ) {
			throw new IllegalArgumentException( "At least one member is required" );
		}
		try {
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
			return new MethodHandleMultiValueReader( readers );
		}
		catch (RuntimeException e) {
			LOG.debugf( e, "Failed to create method-handle multi-value reader for %s, falling back to reflection", declaringClass );
			return reflectionFallback.multiValueReader( declaringClass, members );
		}
	}

	@Override
	public MultiValueWriter multiValueWriter(Class<?> declaringClass, Member... members) {
		if ( members.length == 0 ) {
			throw new IllegalArgumentException( "At least one member is required" );
		}
		try {
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
			return new MethodHandleMultiValueWriter( writers );
		}
		catch (RuntimeException e) {
			LOG.debugf( e, "Failed to create method-handle multi-value writer for %s, falling back to reflection", declaringClass );
			return reflectionFallback.multiValueWriter( declaringClass, members );
		}
	}

	private MethodHandles.Lookup privateLookup(Class<?> targetClass) throws IllegalAccessException {
		return MethodHandles.privateLookupIn( targetClass, this.lookup );
	}
}
