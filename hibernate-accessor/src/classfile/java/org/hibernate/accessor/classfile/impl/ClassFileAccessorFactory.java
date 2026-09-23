/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.classfile.impl;

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
import org.hibernate.accessor.classfile.spi.ClassFileBulkAccessor;
import org.hibernate.accessor.spi.AccessorConfiguration;
import org.hibernate.accessor.spi.BytecodeDumper;
import org.hibernate.accessor.spi.CrossClassLoaderLookupBridge;
import org.hibernate.accessor.spi.MemberValidation;

import org.jboss.logging.Logger;

public class ClassFileAccessorFactory implements AccessorFactory {

	private static final Logger LOG = Logger.getLogger( ClassFileAccessorFactory.class );

	private static final MethodHandles.Lookup ACCESSOR_MODULE_LOOKUP = MethodHandles.lookup();
	private final ClassValue<ClassFileClassAccessorInfo> cache;
	private final MethodHandles.Lookup callerLookup;
	private final CrossClassLoaderLookupBridge lookupBridge;
	private final BytecodeDumper bytecodeDumper;
	private final AccessorFactory reflectionFallback = AccessorFactory.reflection();

	public ClassFileAccessorFactory(MethodHandles.Lookup lookup) {
		this( new AccessorConfiguration( lookup ) );
	}

	public ClassFileAccessorFactory(AccessorConfiguration configuration) {
		this.callerLookup = configuration.lookup();
		this.lookupBridge = new CrossClassLoaderLookupBridge( callerLookup, ClassFileBridgeClassGenerator::generate );
		this.bytecodeDumper = new BytecodeDumper( configuration );
		this.cache = new ClassValue<>() {
			@Override
			protected ClassFileClassAccessorInfo computeValue(Class<?> type) {
				return ClassFileClassAccessorInfo.create( type, lookupBridge, callerLookup, bytecodeDumper );
			}
		};
	}

	@Override
	public <T> Instantiator<T> instantiator(Constructor<T> constructor) {
		try {
			ClassFileClassAccessorInfo info = getOrCreate( constructor.getDeclaringClass() );
			return new ClassFileInstantiator<>( info.bulkAccessor(), info.constructorIndex( constructor ) );
		}
		catch (RuntimeException e) {
			LOG.debugf( e, "Failed to create ClassFile instantiator for %s, falling back to reflection", constructor.getDeclaringClass() );
			return reflectionFallback.instantiator( constructor );
		}
	}

	@Override
	public ValueReader<?> valueReader(Field field) {
		MemberValidation.validateInstanceMember( field );
		try {
			ClassFileClassAccessorInfo info = getOrCreate( field.getDeclaringClass() );
			return new ClassFileFieldValueReader<>( info.bulkAccessor(), info.fieldIndex( field ) );
		}
		catch (RuntimeException e) {
			LOG.debugf( e, "Failed to create ClassFile value reader for %s, falling back to reflection", field );
			return reflectionFallback.valueReader( field );
		}
	}

	@Override
	public ValueReader<?> valueReader(Method method) {
		MemberValidation.validateReaderMethod( method );
		try {
			ClassFileClassAccessorInfo info = getOrCreate( method.getDeclaringClass() );
			return new ClassFileMethodValueReader<>( info.bulkAccessor(), info.methodIndex( method ) );
		}
		catch (RuntimeException e) {
			LOG.debugf( e, "Failed to create ClassFile value reader for %s, falling back to reflection", method );
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
			ClassFileClassAccessorInfo info = getOrCreate( field.getDeclaringClass() );
			return new ClassFileFieldValueWriter( info.bulkAccessor(), info.fieldIndex( field ) );
		}
		catch (RuntimeException e) {
			LOG.debugf( e, "Failed to create ClassFile value writer for %s, falling back to reflection", field );
			return reflectionFallback.valueWriter( field );
		}
	}

	@Override
	public ValueWriter valueWriter(Method setter) {
		MemberValidation.validateWriterMethod( setter );
		try {
			ClassFileClassAccessorInfo info = getOrCreate( setter.getDeclaringClass() );
			return new ClassFileMethodValueWriter( info.bulkAccessor(), info.methodIndex( setter ) );
		}
		catch (RuntimeException e) {
			LOG.debugf( e, "Failed to create ClassFile value writer for %s, falling back to reflection", setter );
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
			LOG.debugf( e, "Failed to create ClassFile multi-value reader for %s, falling back to reflection", declaringClass );
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
		try {
			if ( allSameDeclaringClass( declaringClass, members ) ) {
				return generateDirectWriter( members );
			}
			return generateBulkBasedWriter( members );
		}
		catch (RuntimeException e) {
			LOG.debugf( e, "Failed to create ClassFile multi-value writer for %s, falling back to reflection", declaringClass );
			return reflectionFallback.multiValueWriter( declaringClass, members );
		}
	}

	private MultiValueReader generateDirectReader(Member[] members) {
		Class<?> targetClass = members[0].getDeclaringClass();
		byte[] bytecode = ClassFileMultiValueClassGenerator.generateReader( targetClass, members );
		bytecodeDumper.dump( targetClass.getName().replace( '.', '/' ) + "$$HibernateAccessorMultiReader_" + java.util.UUID.randomUUID(), bytecode );
		try {
			return (MultiValueReader) lookupBridge.defineAccessor( callerLookup, targetClass, bytecode );
		}
		catch (Exception e) {
			throw new MultiValueAccessorGenerationException(
					"Failed to create direct multi-value reader for " + targetClass.getName(), e );
		}
	}

	private MultiValueWriter generateDirectWriter(Member[] members) {
		Class<?> targetClass = members[0].getDeclaringClass();
		byte[] bytecode = ClassFileMultiValueClassGenerator.generateWriter( targetClass, members );
		bytecodeDumper.dump( targetClass.getName().replace( '.', '/' ) + "$$HibernateAccessorMultiWriter_" + java.util.UUID.randomUUID(), bytecode );
		try {
			return (MultiValueWriter) lookupBridge.defineAccessor( callerLookup, targetClass, bytecode );
		}
		catch (Exception e) {
			throw new MultiValueAccessorGenerationException(
					"Failed to create direct multi-value writer for " + targetClass.getName(), e );
		}
	}

	private MultiValueReader generateBulkBasedReader(Member[] members) {
		BulkAccessorLayout layout = buildBulkAccessorLayout( members );
		byte[] bytecode = ClassFileMultiValueClassGenerator.generateBulkReader(
				layout.accesses,
				layout.accessors.length
		);
		bytecodeDumper.dump( members[0].getDeclaringClass().getName().replace( '.', '/' ) + "$$HibernateAccessorMultiBulkReader_" + java.util.UUID.randomUUID(), bytecode );
		try {
			MethodHandles.Lookup hiddenLookup = ACCESSOR_MODULE_LOOKUP.defineHiddenClass( bytecode, true );
			Class<?>[] paramTypes = new Class<?>[layout.accessors.length];
			Arrays.fill( paramTypes, ClassFileBulkAccessor.class );
			return (MultiValueReader) hiddenLookup.lookupClass()
					.getDeclaredConstructor( paramTypes )
					.newInstance( (Object[]) layout.accessors );
		}
		catch (Exception e) {
			throw new MultiValueAccessorGenerationException( "Failed to create bulk-based multi-value reader", e );
		}
	}

	private MultiValueWriter generateBulkBasedWriter(Member[] members) {
		BulkAccessorLayout layout = buildBulkAccessorLayout( members );
		byte[] bytecode = ClassFileMultiValueClassGenerator.generateBulkWriter(
				layout.accesses,
				layout.accessors.length
		);
		bytecodeDumper.dump( "org/hibernate/accessor/classfile/impl/HibernateAccessorMultiBulkWriter", bytecode );
		try {
			MethodHandles.Lookup hiddenLookup = ACCESSOR_MODULE_LOOKUP.defineHiddenClass( bytecode, true );
			Class<?>[] paramTypes = new Class<?>[layout.accessors.length];
			Arrays.fill( paramTypes, ClassFileBulkAccessor.class );
			return (MultiValueWriter) hiddenLookup.lookupClass()
					.getDeclaredConstructor( paramTypes )
					.newInstance( (Object[]) layout.accessors );
		}
		catch (Exception e) {
			throw new MultiValueAccessorGenerationException( "Failed to create bulk-based multi-value writer", e );
		}
	}

	private BulkAccessorLayout buildBulkAccessorLayout(Member[] members) {
		Map<Class<?>, Integer> classToFieldIndex = new LinkedHashMap<>();
		for ( Member member : members ) {
			classToFieldIndex.computeIfAbsent( member.getDeclaringClass(), cls -> classToFieldIndex.size() );
		}

		ClassFileBulkAccessor[] accessors = new ClassFileBulkAccessor[classToFieldIndex.size()];
		ClassFileClassAccessorInfo[] infos = new ClassFileClassAccessorInfo[classToFieldIndex.size()];
		for ( var entry : classToFieldIndex.entrySet() ) {
			ClassFileClassAccessorInfo info = getOrCreate( entry.getKey() );
			accessors[entry.getValue()] = info.bulkAccessor();
			infos[entry.getValue()] = info;
		}

		BulkMemberAccess[] accesses = new BulkMemberAccess[members.length];
		for ( int i = 0; i < members.length; i++ ) {
			int fieldIdx = classToFieldIndex.get( members[i].getDeclaringClass() );
			ClassFileClassAccessorInfo info = infos[fieldIdx];
			boolean isField = members[i] instanceof Field;
			int memberIdx = isField ? info.fieldIndex( (Field) members[i] ) : info.methodIndex( (Method) members[i] );
			accesses[i] = new BulkMemberAccess( fieldIdx, memberIdx, isField );
		}

		return new BulkAccessorLayout( accesses, accessors );
	}

	private record BulkAccessorLayout(  BulkMemberAccess[] accesses,
										ClassFileBulkAccessor[] accessors) {
	}

	private static boolean allSameDeclaringClass(Class<?> declaringClass, Member[] members) {
		for ( Member member : members ) {
			if ( member.getDeclaringClass() != declaringClass ) {
				return false;
			}
		}
		return true;
	}

	private ClassFileClassAccessorInfo getOrCreate(Class<?> declaringClass) {
		return cache.get( declaringClass );
	}
}
