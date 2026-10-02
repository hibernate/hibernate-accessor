package org.hibernate.accessor.asm.impl;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.accessor.AccessorException;
import org.hibernate.accessor.asm.spi.AsmBulkAccessor;
import org.hibernate.accessor.spi.BulkAccessorMetadata;
import org.hibernate.accessor.spi.BytecodeDumper;
import org.hibernate.accessor.spi.CrossClassLoaderLookupBridge;

import org.objectweb.asm.Type;

final class AsmBulkAccessorBuilder {

	private AsmBulkAccessorBuilder() {
	}

	static AsmBulkAccessor create(Class<?> declaringClass, CrossClassLoaderLookupBridge lookupBridge, java.lang.invoke.MethodHandles.Lookup callerLookup, BytecodeDumper bytecodeDumper) {
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
			constructorIndices.put( Type.getConstructorDescriptor( constructors[i] ), i );
		}

		byte[] bytecode = AsmBulkAccessorClassGenerator.generate( declaringClass, fields, getterMethods, setterMethods, constructors );
		bytecodeDumper.dump( Type.getInternalName( declaringClass ) + "$$HibernateAccessor", bytecode );

		try {
			BulkAccessorMetadata metadata = new BulkAccessorMetadata(
					fieldIndices, getterMethodIndices, setterMethodIndices, constructorIndices );
			return (AsmBulkAccessor) lookupBridge.defineAccessor(
					callerLookup, declaringClass, bytecode, new Class<?>[] { BulkAccessorMetadata.class }, metadata );
		}
		catch (Exception e) {
			throw new AccessorException( "Failed to create bulk accessor for " + declaringClass.getName(), e );
		}
	}

	private static String methodKey(Method method) {
		return method.getName() + Type.getMethodDescriptor( method );
	}
}
