/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.asm.impl;

import java.lang.invoke.MethodHandles;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.hibernate.accessor.AccessorException;
import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.Instantiator;
import org.hibernate.accessor.MultiValueAccessorGenerationException;
import org.hibernate.accessor.MultiValueReader;
import org.hibernate.accessor.MultiValueWriter;
import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.asm.AsmAccessorConfiguration;
import org.hibernate.accessor.asm.AsmGenerationStrategy;
import org.hibernate.accessor.asm.spi.AsmBulkAccessor;
import org.hibernate.accessor.spi.AccessorConfiguration;
import org.hibernate.accessor.spi.BytecodeDumper;
import org.hibernate.accessor.spi.CrossClassLoaderLookupBridge;
import org.hibernate.accessor.spi.MemberValidation;

import org.jboss.logging.Logger;

import org.objectweb.asm.Type;

public class AsmAccessorFactory implements org.hibernate.accessor.asm.AsmAccessorFactory {

	private static final Logger LOG = Logger.getLogger( AsmAccessorFactory.class );

	// we only need it to create hidden classes for generated multi readers/writers
	private static final MethodHandles.Lookup ACCESSOR_MODULE_LOOKUP = MethodHandles.lookup();
	private final ClassValue<AtomicReference<WeakReference<AsmClassAccessorInfo>>> cache;
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
	private final MethodHandles.Lookup callerLookup;
	private final CrossClassLoaderLookupBridge lookupBridge;
	private final BytecodeDumper bytecodeDumper;
	private final AsmGenerationStrategy generationStrategy;
	private final AccessorFactory reflectionFallback = AccessorFactory.reflection();

	public AsmAccessorFactory(MethodHandles.Lookup lookup) {
		this( new AccessorConfiguration( lookup ) );
	}

	public AsmAccessorFactory(AccessorConfiguration configuration) {
		this.callerLookup = configuration.lookup();
		this.lookupBridge = new CrossClassLoaderLookupBridge( callerLookup, AsmBridgeClassGenerator::generate );
		this.bytecodeDumper = new BytecodeDumper( configuration );
		this.generationStrategy = AsmAccessorConfiguration.generationStrategy( configuration );
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
			AsmClassAccessorInfo info = getOrCreate( constructor.getDeclaringClass() );
			return new AsmInstantiator<>( info.bulkAccessor(), info, info.constructorIndex( constructor ), constructor.getParameterCount() );
		}
		catch (RuntimeException e) {
			LOG.debugf( e, "Failed to create ASM instantiator for %s, falling back to reflection", constructor.getDeclaringClass() );
			return reflectionFallback.instantiator( constructor );
		}
	}

	@Override
	public ValueReader<?> valueReader(Field field) {
		MemberValidation.validateInstanceMember( field );
		try {
			if ( generationStrategy == AsmGenerationStrategy.PER_MEMBER ) {
				return perMemberReaders.get( field.getDeclaringClass() ).computeIfAbsent( field, this::generatePerMemberReader );
			}
			AsmClassAccessorInfo info = getOrCreate( field.getDeclaringClass() );
			return new AsmFieldValueReader<>( info.bulkAccessor(), info, info.fieldIndex( field ) );
		}
		catch (RuntimeException e) {
			LOG.debugf( e, "Failed to create ASM value reader for %s, falling back to reflection", field );
			return reflectionFallback.valueReader( field );
		}
	}

	@Override
	public ValueReader<?> valueReader(Method method) {
		MemberValidation.validateReaderMethod( method );
		try {
			if ( generationStrategy == AsmGenerationStrategy.PER_MEMBER ) {
				return perMemberReaders.get( method.getDeclaringClass() ).computeIfAbsent( method, this::generatePerMemberReader );
			}
			AsmClassAccessorInfo info = getOrCreate( method.getDeclaringClass() );
			return new AsmMethodValueReader<>( info.bulkAccessor(), info, info.methodIndex( method ) );
		}
		catch (RuntimeException e) {
			LOG.debugf( e, "Failed to create ASM value reader for %s, falling back to reflection", method );
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
			if ( generationStrategy == AsmGenerationStrategy.PER_MEMBER ) {
				return perMemberWriters.get( field.getDeclaringClass() ).computeIfAbsent( field, this::generatePerMemberWriter );
			}
			AsmClassAccessorInfo info = getOrCreate( field.getDeclaringClass() );
			return new AsmFieldValueWriter( info.bulkAccessor(), info, info.fieldIndex( field ) );
		}
		catch (RuntimeException e) {
			LOG.debugf( e, "Failed to create ASM value writer for %s, falling back to reflection", field );
			return reflectionFallback.valueWriter( field );
		}
	}

	@Override
	public ValueWriter valueWriter(Method setter) {
		MemberValidation.validateWriterMethod( setter );
		try {
			if ( generationStrategy == AsmGenerationStrategy.PER_MEMBER ) {
				return perMemberWriters.get( setter.getDeclaringClass() ).computeIfAbsent( setter, this::generatePerMemberWriter );
			}
			AsmClassAccessorInfo info = getOrCreate( setter.getDeclaringClass() );
			return new AsmMethodValueWriter( info.bulkAccessor(), info, info.methodIndex( setter ) );
		}
		catch (RuntimeException e) {
			LOG.debugf( e, "Failed to create ASM value writer for %s, falling back to reflection", setter );
			return reflectionFallback.valueWriter( setter );
		}
	}

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

	private MultiValueReader generateDirectReader(Member[] members) {
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

	private MultiValueWriter generateDirectWriter(Member[] members) {
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

	private MultiValueReader generateBulkBasedReader(Member[] members) {
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

	private MultiValueWriter generateBulkBasedWriter(Member[] members) {
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

	private BulkAccessorLayout buildBulkAccessorLayout(Member[] members) {
		final Map<Class<?>, Integer> classToFieldIndex = new LinkedHashMap<>();
		for ( Member member : members ) {
			classToFieldIndex.computeIfAbsent( member.getDeclaringClass(), cls -> classToFieldIndex.size() );
		}

		final AsmBulkAccessor[] accessors = new AsmBulkAccessor[classToFieldIndex.size()];
		final AsmClassAccessorInfo[] infos = new AsmClassAccessorInfo[classToFieldIndex.size()];
		for ( var entry : classToFieldIndex.entrySet() ) {
			final AsmClassAccessorInfo info = getOrCreate( entry.getKey() );
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

	private record BulkAccessorLayout(  BulkMemberAccess[] accesses,
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

	private AsmClassAccessorInfo getOrCreate(Class<?> declaringClass) {
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
