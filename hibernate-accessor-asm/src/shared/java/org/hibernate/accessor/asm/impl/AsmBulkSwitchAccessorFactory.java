/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.asm.impl;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicReference;

import org.hibernate.accessor.Instantiator;
import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.spi.AccessorConfiguration;
import org.hibernate.accessor.spi.MemberValidation;

/**
 * ASM-based accessor factory using the BULK_SWITCH generation strategy.
 * Generates one bulk accessor class per entity with TABLESWITCH dispatch on member index.
 * Single-value readers/writers are thin records wrapping the bulk accessor.
 */
public class AsmBulkSwitchAccessorFactory extends AbstractAsmAccessorFactory {

	private final ClassValue<AtomicReference<WeakReference<AsmClassAccessorInfo>>> cache;

	public AsmBulkSwitchAccessorFactory(AccessorConfiguration configuration) {
		super( configuration );
		this.cache = new ClassValue<>() {
			@Override
			protected AtomicReference<WeakReference<AsmClassAccessorInfo>> computeValue(Class<?> type) {
				return new AtomicReference<>();
			}
		};
	}

	@Override
	public <T> Instantiator<T> instantiator(Constructor<T> constructor) {
		try {
			AsmClassAccessorInfo info = getOrCreateClassAccessorInfo( constructor.getDeclaringClass() );
			return new AsmInstantiator<>( info.bulkAccessor(), info, info.constructorIndex( constructor ), constructor.getParameterCount() );
		}
		catch (RuntimeException e) {
			getLogger().debugf( e, "Failed to create ASM instantiator for %s, falling back to reflection", constructor.getDeclaringClass() );
			return reflectionFallback.instantiator( constructor );
		}
	}

	@Override
	public ValueReader<?> valueReader(Field field) {
		MemberValidation.validateInstanceMember( field );
		try {
			AsmClassAccessorInfo info = getOrCreateClassAccessorInfo( field.getDeclaringClass() );
			return new AsmFieldValueReader<>( info.bulkAccessor(), info, info.fieldIndex( field ) );
		}
		catch (RuntimeException e) {
			getLogger().debugf( e, "Failed to create ASM value reader for %s, falling back to reflection", field );
			return reflectionFallback.valueReader( field );
		}
	}

	@Override
	public ValueReader<?> valueReader(Method method) {
		MemberValidation.validateReaderMethod( method );
		try {
			AsmClassAccessorInfo info = getOrCreateClassAccessorInfo( method.getDeclaringClass() );
			return new AsmMethodValueReader<>( info.bulkAccessor(), info, info.methodIndex( method ) );
		}
		catch (RuntimeException e) {
			getLogger().debugf( e, "Failed to create ASM value reader for %s, falling back to reflection", method );
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
			AsmClassAccessorInfo info = getOrCreateClassAccessorInfo( field.getDeclaringClass() );
			return new AsmFieldValueWriter( info.bulkAccessor(), info, info.fieldIndex( field ) );
		}
		catch (RuntimeException e) {
			getLogger().debugf( e, "Failed to create ASM value writer for %s, falling back to reflection", field );
			return reflectionFallback.valueWriter( field );
		}
	}

	@Override
	public ValueWriter valueWriter(Method setter) {
		MemberValidation.validateWriterMethod( setter );
		try {
			AsmClassAccessorInfo info = getOrCreateClassAccessorInfo( setter.getDeclaringClass() );
			return new AsmMethodValueWriter( info.bulkAccessor(), info, info.methodIndex( setter ) );
		}
		catch (RuntimeException e) {
			getLogger().debugf( e, "Failed to create ASM value writer for %s, falling back to reflection", setter );
			return reflectionFallback.valueWriter( setter );
		}
	}

	@Override
	protected AsmClassAccessorInfo getOrCreateClassAccessorInfo(Class<?> declaringClass) {
		// A ClassValue entry can outlive its ClassValue until the key's map is cleaned.
		// Do not attach a library-defined value strongly to a longer-lived entity class:
		// it would retain the library's loader even after the factory is discarded.
		var slot = cache.get( declaringClass );
		var current = slot.get();
		AsmClassAccessorInfo cached = current == null ? null : current.get();
		if ( cached != null ) {
			return cached;
		}
		synchronized (slot) {
			var reference = slot.get();
			AsmClassAccessorInfo info = reference == null ? null : reference.get();
			if ( info == null ) {
				info = AsmClassAccessorInfo.create( declaringClass, lookupBridge, callerLookup, bytecodeDumper );
				slot.set( new WeakReference<>( info ) );
			}
			return info;
		}
	}
}
