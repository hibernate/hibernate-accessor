/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.asm.impl;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.Instantiator;
import org.hibernate.accessor.MultiValueAccessorGenerationException;
import org.hibernate.accessor.MultiValueReader;
import org.hibernate.accessor.MultiValueWriter;
import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.asm.spi.AsmBulkAccessor;
import org.hibernate.accessor.spi.AccessorConfiguration;
import org.hibernate.accessor.spi.BytecodeDumper;
import org.hibernate.accessor.spi.CrossClassLoaderLookupBridge;
import org.hibernate.accessor.spi.MemberValidation;

import org.jboss.logging.Logger;

import org.objectweb.asm.Type;

/**
 * Abstract base class for ASM-based accessor factories.
 * Provides shared logic for multi-value accessors and common infrastructure.
 */
public abstract class AbstractAsmAccessorFactory implements org.hibernate.accessor.asm.AsmAccessorFactory {

	private static final Logger LOG = Logger.getLogger( AbstractAsmAccessorFactory.class );

	// we only need it to create hidden classes for generated multi readers/writers
	private static final MethodHandles.Lookup ACCESSOR_MODULE_LOOKUP = MethodHandles.lookup();

	protected final MethodHandles.Lookup callerLookup;
	protected final CrossClassLoaderLookupBridge lookupBridge;
	protected final BytecodeDumper bytecodeDumper;
	protected final AccessorFactory reflectionFallback;

	protected AbstractAsmAccessorFactory(AccessorConfiguration configuration) {
		this.callerLookup = configuration.lookup();
		this.reflectionFallback = AccessorFactory.reflection( configuration );
		this.lookupBridge = new CrossClassLoaderLookupBridge( configuration.accessContext(), AsmBridgeClassGenerator::generate,
				AccessorFactory.class.getModule(), AbstractAsmAccessorFactory.class.getModule() );
		this.bytecodeDumper = new BytecodeDumper( configuration );
	}

	@Override
	public abstract <T> Instantiator<T> instantiator(Constructor<T> constructor);

	@Override
	public abstract ValueReader<?> valueReader(Field field);

	@Override
	public abstract ValueReader<?> valueReader(Method method);

	@Override
	public abstract ValueWriter valueWriter(Field field);

	@Override
	public abstract ValueWriter valueWriter(Method setter);

	@Override
	public MultiValueReader multiValueReader(Class<?> declaringClass, Member... members) {
		if ( members.length == 0 ) {
			throw new IllegalArgumentException( "At least one member is required" );
		}
		for ( Member member : members ) {
			MemberValidation.validateMemberDeclaringType( declaringClass, member );
			MemberValidation.validateReaderMember( member );
		}
		try {
			if ( allSameDeclaringClass( declaringClass, members ) ) {
				return generateDirectReader( members );
			}
			return generateBulkBasedReader( members );
		}
		catch (RuntimeException e) {
			LOG.debugf( e, "Failed to create ASM multi-value reader for %s, falling back to reflection", declaringClass );
			return reflectionFallback.multiValueReader( declaringClass, members );
		}
	}

	@Override
	public MultiValueWriter multiValueWriter(Class<?> declaringClass, Member... members) {
		if ( members.length == 0 ) {
			throw new IllegalArgumentException( "At least one member is required" );
		}
		for ( Member member : members ) {
			MemberValidation.validateMemberDeclaringType( declaringClass, member );
			MemberValidation.validateWriterMember( member );
		}
		// Nestmate access does not permit PUTFIELD on another class's final field.
		// Reject before the generation/fallback block so the caller can use per-property access.
		for ( Member member : members ) {
			if ( member instanceof Field field && Modifier.isFinal( field.getModifiers() ) ) {
				throw new MultiValueAccessorGenerationException( "Cannot generate a multi-value writer for final field " + field );
			}
		}
		try {
			if ( allSameDeclaringClass( declaringClass, members ) ) {
				return generateDirectWriter( members );
			}
			return generateBulkBasedWriter( members );
		}
		catch (RuntimeException e) {
			LOG.debugf( e, "Failed to create ASM multi-value writer for %s, falling back to reflection", declaringClass );
			return reflectionFallback.multiValueWriter( declaringClass, members );
		}
	}

	protected MultiValueReader generateDirectReader(Member[] members) {
		final Class<?> targetClass = members[0].getDeclaringClass();
		final byte[] bytecode = AsmMultiValueClassGenerator.generateReader( targetClass, members );
		bytecodeDumper.dump( Type.getInternalName( targetClass ) + "$$HibernateAccessorMultiReader_" + java.util.UUID.randomUUID(), bytecode );
		try {
			return (MultiValueReader) lookupBridge.defineAccessor( callerLookup, targetClass, bytecode );
		}
		catch (Exception e) {
			throw new MultiValueAccessorGenerationException(
					"Failed to create direct multi-value reader for " + targetClass.getName(), e );
		}
	}

	protected MultiValueWriter generateDirectWriter(Member[] members) {
		final Class<?> targetClass = members[0].getDeclaringClass();
		final byte[] bytecode = AsmMultiValueClassGenerator.generateWriter( targetClass, members );
		bytecodeDumper.dump( Type.getInternalName( targetClass ) + "$$HibernateAccessorMultiWriter_" + java.util.UUID.randomUUID(), bytecode );
		try {
			return (MultiValueWriter) lookupBridge.defineAccessor( callerLookup, targetClass, bytecode );
		}
		catch (Exception e) {
			throw new MultiValueAccessorGenerationException(
					"Failed to create direct multi-value writer for " + targetClass.getName(), e );
		}
	}

	protected MultiValueReader generateBulkBasedReader(Member[] members) {
		final BulkAccessorLayout layout = buildBulkAccessorLayout( members );
		final byte[] bytecode = AsmMultiValueClassGenerator.generateBulkReader(
				layout.accesses,
				layout.accessors.length
		);
		bytecodeDumper.dump( Type.getInternalName( members[0].getDeclaringClass() ) + "$$HibernateAccessorMultiBulkReader_" + java.util.UUID.randomUUID(), bytecode );
		try {
			final MethodHandles.Lookup hiddenLookup = ACCESSOR_MODULE_LOOKUP.defineHiddenClass( bytecode, true );
			final Class<?>[] paramTypes = new Class<?>[layout.accessors.length];
			Arrays.fill( paramTypes, AsmBulkAccessor.class );
			return (MultiValueReader) hiddenLookup.lookupClass()
					.getDeclaredConstructor( paramTypes )
					.newInstance( (Object[]) layout.accessors );
		}
		catch (Exception e) {
			throw new MultiValueAccessorGenerationException( "Failed to create bulk-based multi-value reader", e );
		}
	}

	protected MultiValueWriter generateBulkBasedWriter(Member[] members) {
		final BulkAccessorLayout layout = buildBulkAccessorLayout( members );
		final byte[] bytecode = AsmMultiValueClassGenerator.generateBulkWriter(
				layout.accesses,
				layout.accessors.length
		);
		bytecodeDumper.dump( "org/hibernate/accessor/asm/impl/HibernateAccessorMultiBulkWriter", bytecode );
		try {
			final MethodHandles.Lookup hiddenLookup = ACCESSOR_MODULE_LOOKUP.defineHiddenClass( bytecode, true );
			final Class<?>[] paramTypes = new Class<?>[layout.accessors.length];
			Arrays.fill( paramTypes, AsmBulkAccessor.class );
			return (MultiValueWriter) hiddenLookup.lookupClass()
					.getDeclaredConstructor( paramTypes )
					.newInstance( (Object[]) layout.accessors );
		}
		catch (Exception e) {
			throw new MultiValueAccessorGenerationException( "Failed to create bulk-based multi-value writer", e );
		}
	}

	/**
	 * Template method for subclasses to provide bulk accessor info for their target class.
	 * Used by multi-value accessor generation.
	 */
	protected abstract AsmClassAccessorInfo getOrCreateClassAccessorInfo(Class<?> declaringClass);

	private BulkAccessorLayout buildBulkAccessorLayout(Member[] members) {
		final Map<Class<?>, Integer> classToFieldIndex = new LinkedHashMap<>();
		for ( Member member : members ) {
			classToFieldIndex.computeIfAbsent( member.getDeclaringClass(), cls -> classToFieldIndex.size() );
		}

		final AsmBulkAccessor[] accessors = new AsmBulkAccessor[classToFieldIndex.size()];
		final AsmClassAccessorInfo[] infos = new AsmClassAccessorInfo[classToFieldIndex.size()];
		for ( var entry : classToFieldIndex.entrySet() ) {
			final AsmClassAccessorInfo info = getOrCreateClassAccessorInfo( entry.getKey() );
			accessors[entry.getValue()] = info.bulkAccessor();
			infos[entry.getValue()] = info;
		}

		final BulkMemberAccess[] accesses = new BulkMemberAccess[members.length];
		for ( int i = 0; i < members.length; i++ ) {
			final int fieldIdx = classToFieldIndex.get( members[i].getDeclaringClass() );
			final AsmClassAccessorInfo info = infos[fieldIdx];
			final boolean isField = members[i] instanceof Field;
			final int memberIdx = isField ? info.fieldIndex( (Field) members[i] ) : info.methodIndex( (Method) members[i] );
			accesses[i] = new BulkMemberAccess( fieldIdx, memberIdx, isField );
		}

		return new BulkAccessorLayout( accesses, accessors );
	}

	protected record BulkAccessorLayout(BulkMemberAccess[] accesses,
										AsmBulkAccessor[] accessors) {
	}

	private static boolean allSameDeclaringClass(Class<?> declaringClass, Member[] members) {
		if ( members.length == 0 ) {
			return true;
		}
		for ( int i = 0; i < members.length; i++ ) {
			if ( members[i].getDeclaringClass() != declaringClass ) {
				return false;
			}
		}
		return true;
	}

	protected Logger getLogger() {
		return LOG;
	}
}
