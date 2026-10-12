package org.hibernate.accessor.classfile.impl;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.hibernate.accessor.Instantiator;
import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.classfile.spi.ClassFileBulkAccessor;
import org.hibernate.accessor.spi.AccessorConfiguration;

/**
 * ClassFile API-based accessor factory using the BULK_SWITCH generation strategy.
 * Generates one bulk accessor class per entity with TABLESWITCH dispatch on member index.
 * Single-value readers/writers are thin records wrapping the bulk accessor.
 */
public class ClassFileAccessorFactory extends AbstractClassFileAccessorFactory {

	public ClassFileAccessorFactory(java.lang.invoke.MethodHandles.Lookup lookup) {
		this( new AccessorConfiguration( lookup ) );
	}

	public ClassFileAccessorFactory(AccessorConfiguration configuration) {
		super( configuration );
	}

	@Override
	public <T> Instantiator<T> instantiator(Constructor<T> constructor) {
		try {
			ClassFileBulkAccessor accessor = getOrCreateBulkAccessor( constructor.getDeclaringClass() );
			return new ClassFileInstantiator<>( accessor, accessor.constructorIndex( constructor ), constructor.getParameterCount() );
		}
		catch (RuntimeException e) {
			getLogger().debugf( e, "Failed to create ClassFile API instantiator for %s, falling back to reflection", constructor.getDeclaringClass() );
			return reflectionFallback.instantiator( constructor );
		}
	}

	@Override
	public ValueReader<?> doValueReader(Field field) {
		final var accessor = getOrCreateBulkAccessor( field.getDeclaringClass() );
		return new ClassFileFieldValueReader<>( accessor, accessor.fieldIndex( field ) );

	}

	@Override
	public ValueReader<?> doValueReader(Method method) {
		final var accessor = getOrCreateBulkAccessor( method.getDeclaringClass() );
		return new ClassFileMethodValueReader<>( accessor, accessor.methodIndex( method ) );
	}

	@Override
	public ValueWriter doValueWriter(Field field) {
		final var accessor = getOrCreateBulkAccessor( field.getDeclaringClass() );
		return new ClassFileFieldValueWriter( accessor, accessor.fieldIndex( field ) );
	}

	@Override
	public ValueWriter doValueWriter(Method setter) {
		final var accessor = getOrCreateBulkAccessor( setter.getDeclaringClass() );
		return new ClassFileMethodValueWriter( accessor, accessor.methodIndex( setter ) );
	}
}
