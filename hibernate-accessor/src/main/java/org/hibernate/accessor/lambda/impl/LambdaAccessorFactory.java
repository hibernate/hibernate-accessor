/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.lambda.impl;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaConversionException;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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

public class LambdaAccessorFactory implements AccessorFactory {

	private static final Logger LOG = Logger.getLogger( LambdaAccessorFactory.class );

	private final MethodHandles.Lookup lookup;
	private final AccessorFactory reflectionFallback = AccessorFactory.reflection();

	public LambdaAccessorFactory(MethodHandles.Lookup lookup) {
		this( new AccessorConfiguration( lookup ) );
	}

	public LambdaAccessorFactory(AccessorConfiguration configuration) {
		this.lookup = configuration.lookup();
	}

	@Override
	public <T> Instantiator<T> instantiator(Constructor<T> constructor) {
		try {
			return new LambdaInstantiator<>(
					MethodHandles.privateLookupIn( constructor.getDeclaringClass(), this.lookup ),
					constructor
			);
		}
		catch (RuntimeException | IllegalAccessException e) {
			LOG.debugf( e, "Failed to create lambda instantiator for %s, falling back to reflection", constructor );
			return reflectionFallback.instantiator( constructor );
		}
	}

	@Override
	public ValueReader<?> valueReader(Field field) {
		MemberValidation.validateInstanceMember( field );
		try {
			return new LambdaFieldValueReader<>( MethodHandles.privateLookupIn( field.getDeclaringClass(), this.lookup ).unreflectGetter( field ) );
		}
		catch (RuntimeException | IllegalAccessException e) {
			LOG.debugf( e, "Failed to create lambda field reader for %s, falling back to reflection", field );
			return reflectionFallback.valueReader( field );
		}
	}

	@Override
	public ValueReader<?> valueReader(Method method) {
		MemberValidation.validateReaderMethod( method );
		try {
			MethodHandles.Lookup lookup = MethodHandles.privateLookupIn( method.getDeclaringClass(), this.lookup );
			MethodHandle target = lookup.unreflect( method );
			try {
				CallSite site = LambdaMetafactory.metafactory(
						lookup,
						"get",
						MethodType.methodType( ValueReader.class ),
						MethodType.methodType( Object.class, Object.class ),
						target,
						MethodType.methodType( method.getReturnType(), method.getDeclaringClass() )
				);
				return (ValueReader<?>) site.getTarget().invokeExact();
			}
			catch (LambdaConversionException e) {
				// LambdaMetafactory internally calls defineHiddenClass which requires
				// full privilege access (MODULE bit). Cross-classloader lookups lose
				// MODULE (JDK-8228624), so metafactory fails. Fall back to MethodHandle
				// which only needs PRIVATE access and works cross-CL.
				return new LambdaFieldValueReader<>( target );
			}
		}
		catch (Throwable t) {
			if ( t instanceof Error ) {
				throw (Error) t;
			}
			LOG.debugf( t, "Failed to create lambda method reader for %s, falling back to reflection", method );
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
			return new LambdaFieldValueWriter( MethodHandles.privateLookupIn( field.getDeclaringClass(), this.lookup ).unreflectSetter( field ) );
		}
		catch (IllegalAccessException t) {
			LOG.debugf( t, "Failed to create lambda field writer for %s, falling back to reflection", field );
			return reflectionFallback.valueWriter( field );
		}
	}

	@Override
	public ValueWriter valueWriter(Method setter) {
		MemberValidation.validateWriterMethod( setter );
		try {
			MethodHandles.Lookup lookup = MethodHandles.privateLookupIn( setter.getDeclaringClass(), this.lookup );
			MethodHandle target = lookup.unreflect( setter );

			if ( setter.getParameterTypes()[0].isPrimitive() ) {
				// A metafactory-generated lambda unboxes via a checkcast to the exact wrapper,
				// which rejects a widening value (e.g. an Integer passed to a long setter). The
				// MethodHandle writer invokes through asType, which applies the same
				// unbox-and-widen semantics as reflection, so use it for primitive setters.
				return new LambdaFieldValueWriter( target );
			}

			Class<?> paramType = setter.getParameterTypes()[0];

			try {
				CallSite site = LambdaMetafactory.metafactory(
						lookup,
						"set",
						MethodType.methodType( ValueWriter.class ),
						MethodType.methodType( void.class, Object.class, Object.class ),
						target,
						MethodType.methodType( void.class, setter.getDeclaringClass(), paramType )
				);
				return (ValueWriter) site.getTarget().invokeExact();
			}
			catch (LambdaConversionException e) {
				// See valueReader(Method) — same cross-CL MODULE bit issue (JDK-8228624)
				return new LambdaFieldValueWriter( target );
			}
		}
		catch (Throwable t) {
			if ( t instanceof Error ) {
				throw (Error) t;
			}
			LOG.debugf( t, "Failed to create lambda method writer for %s, falling back to reflection", setter );
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
			return new LambdaMultiValueReader( readers );
		}
		catch (RuntimeException e) {
			LOG.debugf( e, "Failed to create lambda multi-value reader for %s, falling back to reflection", declaringClass );
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
			return new LambdaMultiValueWriter( writers );
		}
		catch (RuntimeException e) {
			LOG.debugf( e, "Failed to create lambda multi-value writer for %s, falling back to reflection", declaringClass );
			return reflectionFallback.multiValueWriter( declaringClass, members );
		}
	}
}
