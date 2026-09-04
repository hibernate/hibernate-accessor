/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
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
import java.util.concurrent.ConcurrentHashMap;

import org.hibernate.accessor.HibernateAccessorException;
import org.hibernate.accessor.HibernateAccessorFactory;
import org.hibernate.accessor.HibernateAccessorInstantiator;
import org.hibernate.accessor.HibernateAccessorMultiValueReader;
import org.hibernate.accessor.HibernateAccessorMultiValueWriter;
import org.hibernate.accessor.HibernateAccessorValueReader;
import org.hibernate.accessor.HibernateAccessorValueWriter;
import org.hibernate.accessor.MultiValueAccessorGenerationException;
import org.hibernate.accessor.asm.HibernateAccessorAsmConfiguration;
import org.hibernate.accessor.asm.HibernateAccessorAsmGenerationStrategy;
import org.hibernate.accessor.asm.spi.HibernateAccessorAsmBulkAccessor;
import org.hibernate.accessor.spi.CrossClassLoaderLookupBridge;
import org.hibernate.accessor.spi.HibernateAccessorBytecodeDumper;
import org.hibernate.accessor.spi.HibernateAccessorConfiguration;
import org.hibernate.accessor.spi.MemberValidation;

import org.jboss.logging.Logger;

import org.objectweb.asm.Type;

public class HibernateAccessorAsmFactory implements org.hibernate.accessor.asm.HibernateAccessorAsmFactory {

	private static final Logger LOG = Logger.getLogger( HibernateAccessorAsmFactory.class );

	// we only need it to create hidden classes for generated multi readers/writers
	private static final MethodHandles.Lookup ACCESSOR_MODULE_LOOKUP = MethodHandles.lookup();
	private final ClassValue<HibernateAccessorAsmClassAccessorInfo> cache;
	// Only used by the PER_MEMBER strategy: memoizes the generated per-member readers/writers so
	// repeated calls for the same member return one shared, stateless instance (keeping call sites
	// monomorphic and avoiding a fresh hidden class per call). Keyed by declaring class via
	// ClassValue so entries are collected when the class's loader is unloaded, then by Member.
	private final ClassValue<PerMemberAccessors> perMemberCache = new ClassValue<>() {
		@Override
		protected PerMemberAccessors computeValue(Class<?> type) {
			return new PerMemberAccessors();
		}
	};
	private final CrossClassLoaderLookupBridge lookupBridge;
	private final HibernateAccessorBytecodeDumper bytecodeDumper;
	private final HibernateAccessorAsmGenerationStrategy generationStrategy;
	private final HibernateAccessorFactory reflectionFallback = HibernateAccessorFactory.reflection();

	public HibernateAccessorAsmFactory(MethodHandles.Lookup lookup) {
		this( new HibernateAccessorConfiguration( lookup ) );
	}

	public HibernateAccessorAsmFactory(HibernateAccessorConfiguration configuration) {
		this.lookupBridge = new CrossClassLoaderLookupBridge( configuration.lookup(), HibernateAccessorAsmBridgeClassGenerator::generate );
		this.bytecodeDumper = new HibernateAccessorBytecodeDumper( configuration );
		this.generationStrategy = HibernateAccessorAsmConfiguration.generationStrategy( configuration );
		this.cache = new ClassValue<>() {
			@Override
			protected HibernateAccessorAsmClassAccessorInfo computeValue(Class<?> type) {
				return HibernateAccessorAsmClassAccessorInfo.create( type, lookupBridge, bytecodeDumper );
			}
		};
	}

	@Override
	public <T> HibernateAccessorInstantiator<T> instantiator(Constructor<T> constructor) {
		try {
			HibernateAccessorAsmClassAccessorInfo info = getOrCreate( constructor.getDeclaringClass() );
			return new HibernateAccessorAsmInstantiator<>( info.bulkAccessor(), info.constructorIndex( constructor ) );
		}
		catch (RuntimeException e) {
			LOG.debugf( e, "Failed to create ASM instantiator for %s, falling back to reflection", constructor.getDeclaringClass() );
			return reflectionFallback.instantiator( constructor );
		}
	}

	@Override
	public HibernateAccessorValueReader<?> valueReader(Field field) {
		MemberValidation.validateInstanceMember( field );
		try {
			if ( generationStrategy == HibernateAccessorAsmGenerationStrategy.PER_MEMBER ) {
				return perMemberCache.get( field.getDeclaringClass() ).readers.computeIfAbsent( field, this::generatePerMemberReader );
			}
			HibernateAccessorAsmClassAccessorInfo info = getOrCreate( field.getDeclaringClass() );
			return new HibernateAccessorAsmFieldValueReader<>( info.bulkAccessor(), info.fieldIndex( field ) );
		}
		catch (RuntimeException e) {
			LOG.debugf( e, "Failed to create ASM value reader for %s, falling back to reflection", field );
			return reflectionFallback.valueReader( field );
		}
	}

	@Override
	public HibernateAccessorValueReader<?> valueReader(Method method) {
		MemberValidation.validateReaderMethod( method );
		try {
			if ( generationStrategy == HibernateAccessorAsmGenerationStrategy.PER_MEMBER ) {
				return perMemberCache.get( method.getDeclaringClass() ).readers.computeIfAbsent( method, this::generatePerMemberReader );
			}
			HibernateAccessorAsmClassAccessorInfo info = getOrCreate( method.getDeclaringClass() );
			return new HibernateAccessorAsmMethodValueReader<>( info.bulkAccessor(), info.methodIndex( method ) );
		}
		catch (RuntimeException e) {
			LOG.debugf( e, "Failed to create ASM value reader for %s, falling back to reflection", method );
			return reflectionFallback.valueReader( method );
		}
	}

	@Override
	public HibernateAccessorValueWriter valueWriter(Field field) {
		MemberValidation.validateInstanceMember( field );
		if ( Modifier.isFinal( field.getModifiers() ) ) {
			return reflectionFallback.valueWriter( field );
		}
		try {
			if ( generationStrategy == HibernateAccessorAsmGenerationStrategy.PER_MEMBER ) {
				return perMemberCache.get( field.getDeclaringClass() ).writers.computeIfAbsent( field, this::generatePerMemberWriter );
			}
			HibernateAccessorAsmClassAccessorInfo info = getOrCreate( field.getDeclaringClass() );
			return new HibernateAccessorAsmFieldValueWriter( info.bulkAccessor(), info.fieldIndex( field ) );
		}
		catch (RuntimeException e) {
			LOG.debugf( e, "Failed to create ASM value writer for %s, falling back to reflection", field );
			return reflectionFallback.valueWriter( field );
		}
	}

	@Override
	public HibernateAccessorValueWriter valueWriter(Method setter) {
		MemberValidation.validateWriterMethod( setter );
		try {
			if ( generationStrategy == HibernateAccessorAsmGenerationStrategy.PER_MEMBER ) {
				return perMemberCache.get( setter.getDeclaringClass() ).writers.computeIfAbsent( setter, this::generatePerMemberWriter );
			}
			HibernateAccessorAsmClassAccessorInfo info = getOrCreate( setter.getDeclaringClass() );
			return new HibernateAccessorAsmMethodValueWriter( info.bulkAccessor(), info.methodIndex( setter ) );
		}
		catch (RuntimeException e) {
			LOG.debugf( e, "Failed to create ASM value writer for %s, falling back to reflection", setter );
			return reflectionFallback.valueWriter( setter );
		}
	}

	@Override
	public HibernateAccessorMultiValueReader multiValueReader(Class<?> declaringClass, Member... members) {
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
	public HibernateAccessorMultiValueWriter multiValueWriter(Class<?> declaringClass, Member... members) {
		if ( members.length == 0 ) {
			throw new IllegalArgumentException( "At least one member is required" );
		}
		for ( Member member : members ) {
			MemberValidation.validateMemberDeclaringType( declaringClass, member );
			MemberValidation.validateWriterMember( member );
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

	private HibernateAccessorValueReader<?> generatePerMemberReader(Member member) {
		final Class<?> targetClass = member.getDeclaringClass();
		final byte[] bytecode = HibernateAccessorAsmPerMemberClassGenerator.generateReader( member );
		bytecodeDumper.dump( Type.getInternalName( targetClass ) + "$$HibernateAccessorReader_" + member.getName() + "_" + java.util.UUID.randomUUID(), bytecode );
		try {
			return (HibernateAccessorValueReader<?>) lookupBridge.defineAccessor( targetClass, bytecode );
		}
		catch (Exception e) {
			throw new HibernateAccessorException( "Failed to create per-member value reader for " + member, e );
		}
	}

	private HibernateAccessorValueWriter generatePerMemberWriter(Member member) {
		final Class<?> targetClass = member.getDeclaringClass();
		final byte[] bytecode = HibernateAccessorAsmPerMemberClassGenerator.generateWriter( member );
		bytecodeDumper.dump( Type.getInternalName( targetClass ) + "$$HibernateAccessorWriter_" + member.getName() + "_" + java.util.UUID.randomUUID(), bytecode );
		try {
			return (HibernateAccessorValueWriter) lookupBridge.defineAccessor( targetClass, bytecode );
		}
		catch (Exception e) {
			throw new HibernateAccessorException( "Failed to create per-member value writer for " + member, e );
		}
	}

	private HibernateAccessorMultiValueReader generateDirectReader(Member[] members) {
		final Class<?> targetClass = members[0].getDeclaringClass();
		final byte[] bytecode = HibernateAccessorAsmMultiValueClassGenerator.generateReader( targetClass, members );
		bytecodeDumper.dump( Type.getInternalName( targetClass ) + "$$HibernateAccessorMultiReader_" + java.util.UUID.randomUUID(), bytecode );
		try {
			return (HibernateAccessorMultiValueReader) lookupBridge.defineAccessor( targetClass, bytecode );
		}
		catch (Exception e) {
			throw new MultiValueAccessorGenerationException(
					"Failed to create direct multi-value reader for " + targetClass.getName(), e );
		}
	}

	private HibernateAccessorMultiValueWriter generateDirectWriter(Member[] members) {
		final Class<?> targetClass = members[0].getDeclaringClass();
		final byte[] bytecode = HibernateAccessorAsmMultiValueClassGenerator.generateWriter( targetClass, members );
		bytecodeDumper.dump( Type.getInternalName( targetClass ) + "$$HibernateAccessorMultiWriter_" + java.util.UUID.randomUUID(), bytecode );
		try {
			return (HibernateAccessorMultiValueWriter) lookupBridge.defineAccessor( targetClass, bytecode );
		}
		catch (Exception e) {
			throw new MultiValueAccessorGenerationException(
					"Failed to create direct multi-value writer for " + targetClass.getName(), e );
		}
	}

	private HibernateAccessorMultiValueReader generateBulkBasedReader(Member[] members) {
		final BulkAccessorLayout layout = buildBulkAccessorLayout( members );
		final byte[] bytecode = HibernateAccessorAsmMultiValueClassGenerator.generateBulkReader(
				layout.accesses,
				layout.accessors.length
		);
		bytecodeDumper.dump( Type.getInternalName( members[0].getDeclaringClass() ) + "$$HibernateAccessorMultiBulkReader_" + java.util.UUID.randomUUID(), bytecode );
		try {
			final MethodHandles.Lookup hiddenLookup = ACCESSOR_MODULE_LOOKUP.defineHiddenClass( bytecode, true );
			final Class<?>[] paramTypes = new Class<?>[layout.accessors.length];
			Arrays.fill( paramTypes, HibernateAccessorAsmBulkAccessor.class );
			return (HibernateAccessorMultiValueReader) hiddenLookup.lookupClass()
					.getDeclaredConstructor( paramTypes )
					.newInstance( (Object[]) layout.accessors );
		}
		catch (Exception e) {
			throw new MultiValueAccessorGenerationException( "Failed to create bulk-based multi-value reader", e );
		}
	}

	private HibernateAccessorMultiValueWriter generateBulkBasedWriter(Member[] members) {
		final BulkAccessorLayout layout = buildBulkAccessorLayout( members );
		final byte[] bytecode = HibernateAccessorAsmMultiValueClassGenerator.generateBulkWriter(
				layout.accesses,
				layout.accessors.length
		);
		bytecodeDumper.dump( "org/hibernate/accessor/asm/impl/HibernateAccessorMultiBulkWriter", bytecode );
		try {
			final MethodHandles.Lookup hiddenLookup = ACCESSOR_MODULE_LOOKUP.defineHiddenClass( bytecode, true );
			final Class<?>[] paramTypes = new Class<?>[layout.accessors.length];
			Arrays.fill( paramTypes, HibernateAccessorAsmBulkAccessor.class );
			return (HibernateAccessorMultiValueWriter) hiddenLookup.lookupClass()
					.getDeclaredConstructor( paramTypes )
					.newInstance( (Object[]) layout.accessors );
		}
		catch (Exception e) {
			throw new MultiValueAccessorGenerationException( "Failed to create bulk-based multi-value writer", e );
		}
	}

	private BulkAccessorLayout buildBulkAccessorLayout(Member[] members) {
		final Map<Class<?>, Integer> classToFieldIndex = new LinkedHashMap<>();
		for ( Member member : members ) {
			classToFieldIndex.computeIfAbsent( member.getDeclaringClass(), cls -> classToFieldIndex.size() );
		}

		final HibernateAccessorAsmBulkAccessor[] accessors = new HibernateAccessorAsmBulkAccessor[classToFieldIndex.size()];
		final HibernateAccessorAsmClassAccessorInfo[] infos = new HibernateAccessorAsmClassAccessorInfo[classToFieldIndex.size()];
		for ( var entry : classToFieldIndex.entrySet() ) {
			final HibernateAccessorAsmClassAccessorInfo info = getOrCreate( entry.getKey() );
			accessors[entry.getValue()] = info.bulkAccessor();
			infos[entry.getValue()] = info;
		}

		final HibernateAccessorBulkMemberAccess[] accesses = new HibernateAccessorBulkMemberAccess[members.length];
		for ( int i = 0; i < members.length; i++ ) {
			final int fieldIdx = classToFieldIndex.get( members[i].getDeclaringClass() );
			final HibernateAccessorAsmClassAccessorInfo info = infos[fieldIdx];
			final boolean isField = members[i] instanceof Field;
			final int memberIdx = isField ? info.fieldIndex( (Field) members[i] ) : info.methodIndex( (Method) members[i] );
			accesses[i] = new HibernateAccessorBulkMemberAccess( fieldIdx, memberIdx, isField );
		}

		return new BulkAccessorLayout( accesses, accessors );
	}

	private record BulkAccessorLayout(  HibernateAccessorBulkMemberAccess[] accesses,
										HibernateAccessorAsmBulkAccessor[] accessors) {
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

	private HibernateAccessorAsmClassAccessorInfo getOrCreate(Class<?> declaringClass) {
		return cache.get( declaringClass );
	}

	// Per-class holder for memoized PER_MEMBER accessors. Kept a static holder (no reference back to
	// the factory) so a ClassValue entry pins nothing but its own maps and the members' own class.
	private static final class PerMemberAccessors {
		final ConcurrentHashMap<Member, HibernateAccessorValueReader<?>> readers = new ConcurrentHashMap<>();
		final ConcurrentHashMap<Member, HibernateAccessorValueWriter> writers = new ConcurrentHashMap<>();
	}

}
