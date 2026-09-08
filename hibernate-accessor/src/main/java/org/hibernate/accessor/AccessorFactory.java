/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import org.hibernate.accessor.lambda.impl.LambdaAccessorFactory;
import org.hibernate.accessor.methodhandle.impl.MethodHandleAccessorFactory;
import org.hibernate.accessor.reflection.impl.ReflectionAccessorFactory;
import org.hibernate.accessor.spi.AccessorConfiguration;

/**
 * Factory for creating accessors that read and write object state and instantiate objects.
 *
 * <p>Obtain an instance via the static factory methods {@link #reflection()}, {@link #lambda(MethodHandles.Lookup)},
 * or {@link #methodHandle(MethodHandles.Lookup)},
 * then use it to create {@link Instantiator instantiators},
 * {@link ValueReader readers}, and {@link ValueWriter writers}.
 */
public interface AccessorFactory {

	/**
	 * Returns a reflection-based factory.
	 *
	 * <p>The returned factory uses {@link java.lang.reflect} to access fields, methods, and constructors.
	 *
	 * @return a shared, reflection-based factory instance
	 */
	static AccessorFactory reflection() {
		return ReflectionAccessorFactory.INSTANCE;
	}

	/**
	 * Returns a lambda-based factory that uses the given lookup for access control.
	 *
	 * <p>The returned factory generates lambda-based accessors via {@link java.lang.invoke.LambdaMetafactory},
	 * which can offer better performance than reflection.
	 *
	 * @param lookup the lookup object that determines access rights
	 * @return a new lambda-based factory instance
	 */
	static AccessorFactory lambda(MethodHandles.Lookup lookup) {
		return lambda( new AccessorConfiguration( lookup ) );
	}

	/**
	 * Returns a lambda-based factory with the given configuration.
	 *
	 * @param configuration the accessor configuration (must contain a {@link AccessorConfiguration#LOOKUP lookup})
	 * @return a new lambda-based factory instance
	 */
	static AccessorFactory lambda(AccessorConfiguration configuration) {
		return new LambdaAccessorFactory( configuration );
	}

	/**
	 * Returns a method-handle-based factory that uses the given lookup for access control.
	 *
	 * <p>The returned factory accesses fields, methods, and constructors via
	 * {@link java.lang.invoke.MethodHandle method handles}, which can offer better performance than reflection.
	 *
	 * @param lookup the lookup object that determines access rights
	 * @return a new method-handle-based factory instance
	 */
	static AccessorFactory methodHandle(MethodHandles.Lookup lookup) {
		return methodHandle( new AccessorConfiguration( lookup ) );
	}

	/**
	 * Returns a method-handle-based factory with the given configuration.
	 *
	 * @param configuration the accessor configuration (must contain a {@link AccessorConfiguration#LOOKUP lookup})
	 * @return a new method-handle-based factory instance
	 */
	static AccessorFactory methodHandle(AccessorConfiguration configuration) {
		return new MethodHandleAccessorFactory( configuration );
	}

	/**
	 * Creates an instantiator for the given constructor.
	 *
	 * @param <T> the type to instantiate
	 * @param constructor the constructor to use for instantiation
	 * @return an instantiator that invokes the given constructor
	 * @throws AccessorException if the accessor cannot be created
	 */
	<T> Instantiator<T> instantiator(Constructor<T> constructor);

	/**
	 * Creates a value reader for the given field.
	 *
	 * @param field the field to read from
	 * @return a value reader that reads the field's value from an object instance
	 * @throws AccessorException if the accessor cannot be created
	 */
	ValueReader<?> valueReader(Field field);

	/**
	 * Creates a value reader for the given getter method.
	 *
	 * @param method the getter method to invoke
	 * @return a value reader that reads a value by invoking the method on an object instance
	 * @throws AccessorException if the accessor cannot be created
	 */
	ValueReader<?> valueReader(Method method);

	/**
	 * Creates a value writer for the given field.
	 *
	 * @param field the field to write to
	 * @return a value writer that sets the field's value on an object instance
	 * @throws AccessorException if the accessor cannot be created
	 */
	ValueWriter valueWriter(Field field);

	/**
	 * Creates a value writer for the given setter method.
	 *
	 * @param setter the setter method to invoke
	 * @return a value writer that sets a value by invoking the method on an object instance
	 * @throws AccessorException if the accessor cannot be created
	 */
	ValueWriter valueWriter(Method setter);

	/**
	 * Creates a multi-value reader for the given members.
	 *
	 * <p>Each member may be a {@link Field} or a getter {@link Method}.
	 * The returned reader reads all values in the order the members are specified.
	 *
	 * @param declaringClass the concrete class whose instances will be read;
	 *                       each member must be declared on this class or one of its supertypes
	 * @param members the fields and/or getter methods to read from
	 * @return a multi-value reader that reads all specified members from an object instance
	 * @throws AccessorException if any accessor cannot be created
	 * @throws IllegalArgumentException if a member is not a Field or Method, or is not
	 *         declared on {@code declaringClass} or one of its supertypes
	 */
	MultiValueReader multiValueReader(Class<?> declaringClass, Member... members);

	/**
	 * Creates a multi-value writer for the given members.
	 *
	 * <p>Each member may be a {@link Field} or a setter {@link Method}.
	 * The returned writer writes all values in the order the members are specified.
	 *
	 * @param declaringClass the concrete class whose instances will be written;
	 *                       each member must be declared on this class or one of its supertypes
	 * @param members the fields and/or setter methods to write to
	 * @return a multi-value writer that writes all specified members on an object instance
	 * @throws AccessorException if any accessor cannot be created
	 * @throws IllegalArgumentException if a member is not a Field or Method, or is not
	 *         declared on {@code declaringClass} or one of its supertypes
	 */
	MultiValueWriter multiValueWriter(Class<?> declaringClass, Member... members);
}
