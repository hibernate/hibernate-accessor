/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.classfile.impl;

import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.accessor.AccessorException;
import org.hibernate.accessor.classfile.spi.ClassFileBulkAccessor;
import org.hibernate.accessor.spi.BytecodeDumper;
import org.hibernate.accessor.spi.CrossClassLoaderLookupBridge;

final class ClassFileClassAccessorInfo {

	private final ClassFileBulkAccessor bulkAccessor;
	private final Map<String, Integer> fieldIndices;
	private final Map<String, Integer> getterMethodIndices;
	private final Map<String, Integer> setterMethodIndices;
	private final Map<String, Integer> constructorIndices;

	private ClassFileClassAccessorInfo(ClassFileBulkAccessor bulkAccessor,
			Map<String, Integer> fieldIndices,
			Map<String, Integer> getterMethodIndices,
			Map<String, Integer> setterMethodIndices,
			Map<String, Integer> constructorIndices) {
		this.bulkAccessor = bulkAccessor;
		this.fieldIndices = fieldIndices;
		this.getterMethodIndices = getterMethodIndices;
		this.setterMethodIndices = setterMethodIndices;
		this.constructorIndices = constructorIndices;
	}

	static ClassFileClassAccessorInfo create(Class<?> declaringClass, CrossClassLoaderLookupBridge lookupBridge, java.lang.invoke.MethodHandles.Lookup callerLookup, BytecodeDumper bytecodeDumper) {
		Field[] fields = Arrays.stream( declaringClass.getDeclaredFields() )
				.filter( f -> !Modifier.isStatic( f.getModifiers() ) )
				.toArray( Field[]::new );
		Method[] getterMethods = Arrays.stream( declaringClass.getDeclaredMethods() )
				.filter( m -> !Modifier.isStatic( m.getModifiers() ) )
				.filter( m -> m.getParameterCount() == 0 && m.getReturnType() != void.class )
				.toArray( Method[]::new );
		Method[] setterMethods = Arrays.stream( declaringClass.getDeclaredMethods() )
				.filter( m -> !Modifier.isStatic( m.getModifiers() ) )
				.filter( m -> m.getParameterCount() == 1 )
				.toArray( Method[]::new );
		Constructor<?>[] constructors = declaringClass.getDeclaredConstructors();

		Map<String, Integer> fieldIndices = new HashMap<>();
		for ( int i = 0; i < fields.length; i++ ) {
			fieldIndices.put( fields[i].getName(), i );
		}

		Map<String, Integer> getterMethodIndices = new HashMap<>();
		for ( int i = 0; i < getterMethods.length; i++ ) {
			getterMethodIndices.put( methodKey( getterMethods[i] ), i );
		}

		Map<String, Integer> setterMethodIndices = new HashMap<>();
		for ( int i = 0; i < setterMethods.length; i++ ) {
			setterMethodIndices.put( methodKey( setterMethods[i] ), i );
		}

		Map<String, Integer> constructorIndices = new HashMap<>();
		for ( int i = 0; i < constructors.length; i++ ) {
			constructorIndices.put( constructorDescriptor( constructors[i] ), i );
		}

		byte[] bytecode = ClassFileBulkAccessorClassGenerator.generate( declaringClass, fields, getterMethods, setterMethods, constructors );
		bytecodeDumper.dump( declaringClass.getName().replace( '.', '/' ) + "$$HibernateAccessor", bytecode );

		try {
			ClassFileBulkAccessor instance =
					(ClassFileBulkAccessor) lookupBridge.defineAccessor( callerLookup, declaringClass, bytecode );
			return new ClassFileClassAccessorInfo( instance, fieldIndices, getterMethodIndices, setterMethodIndices, constructorIndices );
		}
		catch (Exception e) {
			throw new AccessorException( "Failed to create bulk accessor for " + declaringClass.getName(), e );
		}
	}

	ClassFileBulkAccessor bulkAccessor() {
		return bulkAccessor;
	}

	int fieldIndex(Field field) {
		Integer index = fieldIndices.get( field.getName() );
		if ( index == null ) {
			throw new AccessorException( "Unknown field: " + field );
		}
		return index;
	}

	int methodIndex(Method method) {
		String key = methodKey( method );
		if ( method.getParameterCount() == 0 && method.getReturnType() != void.class ) {
			Integer index = getterMethodIndices.get( key );
			if ( index != null ) {
				return index;
			}
		}
		else if ( method.getParameterCount() == 1 ) {
			Integer index = setterMethodIndices.get( key );
			if ( index != null ) {
				return index;
			}
		}
		throw new AccessorException( "Unknown method: " + method );
	}

	int constructorIndex(Constructor<?> constructor) {
		Integer index = constructorIndices.get( constructorDescriptor( constructor ) );
		if ( index == null ) {
			throw new AccessorException( "Unknown constructor: " + constructor );
		}
		return index;
	}

	private static String methodKey(Method method) {
		return method.getName() + MethodType.methodType( method.getReturnType(), method.getParameterTypes() ).descriptorString();
	}

	private static String constructorDescriptor(Constructor<?> constructor) {
		return MethodType.methodType( void.class, constructor.getParameterTypes() ).descriptorString();
	}
}
