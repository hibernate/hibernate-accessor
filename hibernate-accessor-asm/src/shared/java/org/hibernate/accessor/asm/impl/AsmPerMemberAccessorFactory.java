/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.asm.impl;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

import org.hibernate.accessor.AccessorException;
import org.hibernate.accessor.Instantiator;
import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.spi.AccessorConfiguration;

import org.objectweb.asm.Type;

/**
 * ASM-based accessor factory using the PER_MEMBER generation strategy.
 * Generates one dedicated accessor class per field/getter or per field/setter.
 * Each generated class contains a single direct field access or method call with no switch.
 */
public class AsmPerMemberAccessorFactory extends AbstractAsmAccessorFactory {

	// JDK-owned containers avoid pinning the implementation loader through stale entries.
	private final ClassValue<ConcurrentHashMap<Member, ValueReader<?>>> perMemberReaders = new ClassValue<>() {
		@Override
		protected ConcurrentHashMap<Member, ValueReader<?>> computeValue(Class<?> type) {
			return new ConcurrentHashMap<>();
		}
	};
	// JDK-owned containers avoid pinning the implementation loader through stale entries.
	private final ClassValue<ConcurrentHashMap<Member, ValueWriter>> perMemberWriters = new ClassValue<>() {
		@Override
		protected ConcurrentHashMap<Member, ValueWriter> computeValue(Class<?> type) {
			return new ConcurrentHashMap<>();
		}
	};

	public AsmPerMemberAccessorFactory(AccessorConfiguration configuration) {
		super( configuration );
	}

	@Override
	public <T> Instantiator<T> instantiator(Constructor<T> constructor) {
		// PER_MEMBER strategy doesn't generate bulk accessors for single-value access,
		// so we delegate instantiator creation to reflection fallback
		return reflectionFallback.instantiator( constructor );
	}

	@Override
	public ValueReader<?> doValueReader(Field field) {
		return perMemberReaders.get( field.getDeclaringClass() ).computeIfAbsent(
				field,
				this::generatePerMemberReader
		);
	}

	@Override
	public ValueReader<?> doValueReader(Method method) {
		return perMemberReaders.get( method.getDeclaringClass() ).computeIfAbsent(
				method,
				this::generatePerMemberReader
		);
	}

	@Override
	public ValueWriter doValueWriter(Field field) {
		return perMemberWriters.get( field.getDeclaringClass() ).computeIfAbsent(
				field,
				this::generatePerMemberWriter
		);
	}

	@Override
	public ValueWriter doValueWriter(Method setter) {
		return perMemberWriters.get( setter.getDeclaringClass() ).computeIfAbsent(
				setter,
				this::generatePerMemberWriter
		);
	}

	private ValueReader<?> generatePerMemberReader(Member member) {
		final Class<?> targetClass = member.getDeclaringClass();
		final byte[] bytecode = AsmPerMemberClassGenerator.generateReader( member );
		bytecodeDumper.dump( Type.getInternalName( targetClass ) + "$$HibernateAccessorReader_" + member.getName() + "_" + java.util.UUID.randomUUID(), bytecode );
		try {
			return (ValueReader<?>) lookupBridge.defineAccessor( callerLookup, targetClass, bytecode );
		}
		catch (Exception e) {
			throw new AccessorException( "Failed to create per-member value reader for " + member, e );
		}
	}

	private ValueWriter generatePerMemberWriter(Member member) {
		final Class<?> targetClass = member.getDeclaringClass();
		final byte[] bytecode = AsmPerMemberClassGenerator.generateWriter( member );
		bytecodeDumper.dump( Type.getInternalName( targetClass ) + "$$HibernateAccessorWriter_" + member.getName() + "_" + java.util.UUID.randomUUID(), bytecode );
		try {
			return (ValueWriter) lookupBridge.defineAccessor( callerLookup, targetClass, bytecode );
		}
		catch (Exception e) {
			throw new AccessorException( "Failed to create per-member value writer for " + member, e );
		}
	}
}
