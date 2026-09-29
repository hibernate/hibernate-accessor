/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.asm.impl;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;

import org.hibernate.accessor.AccessorException;
import org.hibernate.accessor.Instantiator;
import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.spi.AccessorConfiguration;
import org.hibernate.accessor.spi.MemberValidation;

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

	// Cache for bulk accessor info (needed for multi-value accessors)
	private final ClassValue<AsmClassAccessorInfo> bulkAccessorInfoCache = new ClassValue<>() {
		@Override
		protected AsmClassAccessorInfo computeValue(Class<?> type) {
			return AsmClassAccessorInfo.create( type, lookupBridge, callerLookup, bytecodeDumper );
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
	public ValueReader<?> valueReader(Field field) {
		MemberValidation.validateInstanceMember( field );
		try {
			return perMemberReaders.get( field.getDeclaringClass() ).computeIfAbsent( field, this::generatePerMemberReader );
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
			return perMemberReaders.get( method.getDeclaringClass() ).computeIfAbsent( method, this::generatePerMemberReader );
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
			return perMemberWriters.get( field.getDeclaringClass() ).computeIfAbsent( field, this::generatePerMemberWriter );
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
			return perMemberWriters.get( setter.getDeclaringClass() ).computeIfAbsent( setter, this::generatePerMemberWriter );
		}
		catch (RuntimeException e) {
			getLogger().debugf( e, "Failed to create ASM value writer for %s, falling back to reflection", setter );
			return reflectionFallback.valueWriter( setter );
		}
	}

	@Override
	protected AsmClassAccessorInfo getOrCreateClassAccessorInfo(Class<?> declaringClass) {
		return bulkAccessorInfoCache.get( declaringClass );
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
