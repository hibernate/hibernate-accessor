/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.asm.impl;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.hibernate.accessor.Instantiator;
import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.asm.spi.AsmBulkAccessor;
import org.hibernate.accessor.spi.AccessorConfiguration;

/**
 * ASM-based accessor factory using the BULK_SWITCH generation strategy.
 * Generates one bulk accessor class per entity with TABLESWITCH dispatch on member index.
 * Single-value readers/writers are thin records wrapping the bulk accessor.
 */
public class AsmBulkSwitchAccessorFactory extends AbstractAsmAccessorFactory {

	public AsmBulkSwitchAccessorFactory(AccessorConfiguration configuration) {
		super( configuration );
	}

	@Override
	public <T> Instantiator<T> instantiator(Constructor<T> constructor) {
		try {
			AsmBulkAccessor accessor = getOrCreateBulkAccessor( constructor.getDeclaringClass() );
			return new AsmInstantiator<>( accessor, accessor.constructorIndex( constructor ), constructor.getParameterCount() );
		}
		catch (RuntimeException e) {
			getLogger().debugf( e, "Failed to create ASM instantiator for %s, falling back to reflection", constructor.getDeclaringClass() );
			return reflectionFallback.instantiator( constructor );
		}
	}

	@Override
	public ValueReader<?> doValueReader(Field field) {
		final var accessor = getOrCreateBulkAccessor( field.getDeclaringClass() );
		return new AsmFieldValueReader<>( accessor, accessor.fieldIndex( field ) );

	}

	@Override
	public ValueReader<?> doValueReader(Method method) {
		final var accessor = getOrCreateBulkAccessor( method.getDeclaringClass() );
		return new AsmMethodValueReader<>( accessor, accessor.methodIndex( method ) );
	}

	@Override
	public ValueWriter doValueWriter(Field field) {
		final var accessor = getOrCreateBulkAccessor( field.getDeclaringClass() );
		return new AsmFieldValueWriter( accessor, accessor.fieldIndex( field ) );
	}

	@Override
	public ValueWriter doValueWriter(Method setter) {
		final var accessor = getOrCreateBulkAccessor( setter.getDeclaringClass() );
		return new AsmMethodValueWriter( accessor, accessor.methodIndex( setter ) );
	}
}
