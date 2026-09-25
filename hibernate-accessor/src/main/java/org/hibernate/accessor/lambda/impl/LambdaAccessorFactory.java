/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
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
import java.util.HashMap;
import java.util.Map;

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

	// LambdaMetafactory classes are strongly linked to their defining loader. Share
	// successful call sites across factories so repeated requests cannot grow metaspace
	// indefinitely. JDK-owned maps avoid attaching a library-defined holder to the key.
	// Values contain no factory or caller lookup; access is checked before consulting this cache.
	private static final ClassValue<Map<Method, Object>> METHOD_ACCESSORS = new ClassValue<>() {
		@Override
		protected Map<Method, Object> computeValue(Class<?> type) {
			return new HashMap<>();
		}
	};

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
			// Spin only when the lookup and classloader lifetimes permit safe caching.
			if ( !lookup.hasFullPrivilegeAccess() || !canCacheLambdaFor( method.getDeclaringClass() ) ) {
				return new LambdaFieldValueReader<>( target );
			}
			try {
				Map<Method, Object> accessors = METHOD_ACCESSORS.get( method.getDeclaringClass() );
				synchronized (accessors) {
					Object cached = accessors.get( method );
					if ( cached != null ) {
						return (ValueReader<?>) cached;
					}
					CallSite site = LambdaMetafactory.metafactory(
							lookup,
							"get",
							MethodType.methodType( ValueReader.class ),
							MethodType.methodType( Object.class, Object.class ),
							target,
							MethodType.methodType( method.getReturnType(), method.getDeclaringClass() )
					);
					ValueReader<?> accessor = (ValueReader<?>) site.getTarget().invokeExact();
					accessors.put( method, accessor );
					return accessor;
				}
			}
			catch (LambdaConversionException e) {
				// Some method shapes cannot be represented by the metafactory.
				// Keep the already-authorized handle as the fallback.
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

			// Spin only when the lookup and classloader lifetimes permit safe caching.
			if ( !lookup.hasFullPrivilegeAccess() || !canCacheLambdaFor( setter.getDeclaringClass() ) ) {
				return new LambdaFieldValueWriter( target );
			}
			try {
				Map<Method, Object> accessors = METHOD_ACCESSORS.get( setter.getDeclaringClass() );
				synchronized (accessors) {
					Object cached = accessors.get( setter );
					if ( cached != null ) {
						return (ValueWriter) cached;
					}
					CallSite site = LambdaMetafactory.metafactory(
							lookup,
							"set",
							MethodType.methodType( ValueWriter.class ),
							MethodType.methodType( void.class, Object.class, Object.class ),
							target,
							MethodType.methodType( void.class, setter.getDeclaringClass(), paramType )
					);
					ValueWriter accessor = (ValueWriter) site.getTarget().invokeExact();
					accessors.put( setter, accessor );
					return accessor;
				}
			}
			catch (LambdaConversionException e) {
				// As for readers, preserve method-handle support for unsupported lambda shapes.
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

	private static boolean canCacheLambdaFor(Class<?> target) {
		if ( target.isHidden() ) {
			// A strongly linked lambda would keep a weak hidden target alive.
			return false;
		}
		ClassLoader implementationLoader = LambdaAccessorFactory.class.getClassLoader();
		// The target must keep this implementation (and its shared cache) alive.
		// Otherwise reloading the implementation would spin another permanent lambda
		// into a surviving parent/sibling loader on every redeployment.
		for ( ClassLoader loader = target.getClassLoader(); loader != null; loader = loader.getParent() ) {
			if ( loader == implementationLoader ) {
				return true;
			}
		}
		return implementationLoader == null;
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
