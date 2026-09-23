/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.classfile.impl;

import static org.hibernate.accessor.classfile.impl.ClassFileUtils.CONSTRUCTOR_NAME;
import static org.hibernate.accessor.classfile.impl.ClassFileUtils.GENERATED_CLASS_MAJOR_VERSION;
import static org.hibernate.accessor.classfile.impl.ClassFileUtils.classDesc;
import static org.hibernate.accessor.classfile.impl.ClassFileUtils.emitBox;
import static org.hibernate.accessor.classfile.impl.ClassFileUtils.emitIntConstant;
import static org.hibernate.accessor.classfile.impl.ClassFileUtils.emitUnboxOrCast;
import static org.hibernate.accessor.classfile.impl.ClassFileUtils.generatedClassDesc;
import static org.hibernate.accessor.classfile.impl.ClassFileUtils.methodTypeDesc;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import org.hibernate.accessor.MultiValueReader;
import org.hibernate.accessor.MultiValueWriter;
import org.hibernate.accessor.classfile.spi.ClassFileBulkAccessor;

import io.smallrye.classfile.ClassFile;

final class ClassFileMultiValueClassGenerator {

	private static final ClassDesc CD_READER = classDesc( MultiValueReader.class );
	private static final ClassDesc CD_WRITER = classDesc( MultiValueWriter.class );
	private static final ClassDesc CD_BULK_ACCESSOR = classDesc( ClassFileBulkAccessor.class );
	private static final MethodTypeDesc MTD_VOID_INIT = methodTypeDesc( ConstantDescs.CD_void );

	static byte[] generateReader(Class<?> targetClass, Member[] members) {
		String generatedInternalName = targetClass.getName().replace( '.', '/' ) + "$$HibernateAccessorMultiReader";
		ClassDesc generatedDesc = generatedClassDesc( generatedInternalName );

		return ClassFile.of().build( generatedDesc, clb -> {
			clb.with( GENERATED_CLASS_MAJOR_VERSION );
			clb.withFlags( ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER | ClassFile.ACC_SYNTHETIC );
			clb.withSuperclass( ConstantDescs.CD_Object );
			clb.withInterfaceSymbols( CD_READER );

			generateNoArgConstructor( clb );

			clb.withMethod( "get", MethodTypeDesc.of( ConstantDescs.CD_Object.arrayType(), ConstantDescs.CD_Object ), ClassFile.ACC_PUBLIC, mb -> mb.withCode( cb -> {
				emitIntConstant( cb, members.length );
				cb.anewarray( ConstantDescs.CD_Object );
				cb.astore( 2 );

				for ( int i = 0; i < members.length; i++ ) {
					cb.aload( 2 );
					emitIntConstant( cb, i );

					cb.aload( 1 );
					Member member = members[i];
					if ( member instanceof Field field ) {
						ClassDesc ownerDesc = classDesc( field.getDeclaringClass() );
						cb.checkcast( ownerDesc );
						cb.getfield( ownerDesc, field.getName(), classDesc( field.getType() ) );
						emitBox( cb, field.getType() );
					}
					else {
						Method method = (Method) member;
						ClassDesc ownerDesc = classDesc( method.getDeclaringClass() );
						boolean isInterface = method.getDeclaringClass().isInterface();
						cb.checkcast( ownerDesc );
						if ( isInterface ) {
							cb.invokeinterface( ownerDesc, method.getName(), methodTypeDesc( method ) );
						}
						else {
							cb.invokevirtual( ownerDesc, method.getName(), methodTypeDesc( method ) );
						}
						emitBox( cb, method.getReturnType() );
					}

					cb.aastore();
				}

				cb.aload( 2 );
				cb.areturn();
			} )
			);
		} );
	}

	static byte[] generateWriter(Class<?> targetClass, Member[] members) {
		String generatedInternalName = targetClass.getName().replace( '.', '/' ) + "$$HibernateAccessorMultiWriter";
		ClassDesc generatedDesc = generatedClassDesc( generatedInternalName );

		return ClassFile.of().build( generatedDesc, clb -> {
			clb.with( GENERATED_CLASS_MAJOR_VERSION );
			clb.withFlags( ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER | ClassFile.ACC_SYNTHETIC );
			clb.withSuperclass( ConstantDescs.CD_Object );
			clb.withInterfaceSymbols( CD_WRITER );

			generateNoArgConstructor( clb );

			clb.withMethod( "set", MethodTypeDesc.of( ConstantDescs.CD_void, ConstantDescs.CD_Object, ConstantDescs.CD_Object.arrayType() ), ClassFile.ACC_PUBLIC, mb -> mb.withCode( cb -> {
				for ( int i = 0; i < members.length; i++ ) {
					cb.aload( 1 );
					Member member = members[i];
					if ( member instanceof Field field ) {
						ClassDesc ownerDesc = classDesc( field.getDeclaringClass() );
						cb.checkcast( ownerDesc );
						cb.aload( 2 );
						emitIntConstant( cb, i );
						cb.aaload();
						emitUnboxOrCast( cb, field.getType() );
						cb.putfield( ownerDesc, field.getName(), classDesc( field.getType() ) );
					}
					else {
						Method method = (Method) member;
						ClassDesc ownerDesc = classDesc( method.getDeclaringClass() );
						boolean isInterface = method.getDeclaringClass().isInterface();
						cb.checkcast( ownerDesc );
						cb.aload( 2 );
						emitIntConstant( cb, i );
						cb.aaload();
						emitUnboxOrCast( cb, method.getParameterTypes()[0] );
						if ( isInterface ) {
							cb.invokeinterface( ownerDesc, method.getName(), methodTypeDesc( method ) );
						}
						else {
							cb.invokevirtual( ownerDesc, method.getName(), methodTypeDesc( method ) );
						}
						if ( method.getReturnType() != void.class ) {
							if ( method.getReturnType() == long.class || method.getReturnType() == double.class ) {
								cb.pop2();
							}
							else {
								cb.pop();
							}
						}
					}
				}

				cb.return_();
			} )
			);
		} );
	}

	static byte[] generateBulkReader(BulkMemberAccess[] accesses, int accessorFieldCount) {
		String generatedInternalName = "org/hibernate/accessor/classfile/impl/HibernateAccessorMultiBulkReader";
		ClassDesc generatedDesc = generatedClassDesc( generatedInternalName );

		return ClassFile.of().build( generatedDesc, clb -> {
			clb.with( GENERATED_CLASS_MAJOR_VERSION );
			clb.withFlags( ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER | ClassFile.ACC_SYNTHETIC );
			clb.withSuperclass( ConstantDescs.CD_Object );
			clb.withInterfaceSymbols( CD_READER );

			generateBulkAccessorFields( clb, accessorFieldCount );
			generateBulkAccessorConstructor( clb, generatedDesc, accessorFieldCount );

			clb.withMethod( "get", MethodTypeDesc.of( ConstantDescs.CD_Object.arrayType(), ConstantDescs.CD_Object ), ClassFile.ACC_PUBLIC, mb -> mb.withCode( cb -> {
				emitIntConstant( cb, accesses.length );
				cb.anewarray( ConstantDescs.CD_Object );
				cb.astore( 2 );

				for ( int i = 0; i < accesses.length; i++ ) {
					BulkMemberAccess access = accesses[i];
					cb.aload( 2 );
					emitIntConstant( cb, i );

					cb.aload( 0 );
					cb.getfield( generatedDesc, "accessor_" + access.accessorFieldIndex(), CD_BULK_ACCESSOR );
					cb.aload( 1 );
					emitIntConstant( cb, access.memberIndex() );
					String methodName = access.isField() ? "readByField" : "readByMethod";
					cb.invokeinterface( CD_BULK_ACCESSOR, methodName,
							MethodTypeDesc.of( ConstantDescs.CD_Object, ConstantDescs.CD_Object, ConstantDescs.CD_int ) );

					cb.aastore();
				}

				cb.aload( 2 );
				cb.areturn();
			} )
			);
		} );
	}

	static byte[] generateBulkWriter(BulkMemberAccess[] accesses, int accessorFieldCount) {
		String generatedInternalName = "org/hibernate/accessor/classfile/impl/HibernateAccessorMultiBulkWriter";
		ClassDesc generatedDesc = generatedClassDesc( generatedInternalName );

		return ClassFile.of().build( generatedDesc, clb -> {
			clb.with( GENERATED_CLASS_MAJOR_VERSION );
			clb.withFlags( ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER | ClassFile.ACC_SYNTHETIC );
			clb.withSuperclass( ConstantDescs.CD_Object );
			clb.withInterfaceSymbols( CD_WRITER );

			generateBulkAccessorFields( clb, accessorFieldCount );
			generateBulkAccessorConstructor( clb, generatedDesc, accessorFieldCount );

			clb.withMethod( "set", MethodTypeDesc.of( ConstantDescs.CD_void, ConstantDescs.CD_Object, ConstantDescs.CD_Object.arrayType() ), ClassFile.ACC_PUBLIC, mb -> mb.withCode( cb -> {
				for ( int i = 0; i < accesses.length; i++ ) {
					BulkMemberAccess access = accesses[i];
					cb.aload( 0 );
					cb.getfield( generatedDesc, "accessor_" + access.accessorFieldIndex(), CD_BULK_ACCESSOR );
					cb.aload( 1 );
					emitIntConstant( cb, access.memberIndex() );
					cb.aload( 2 );
					emitIntConstant( cb, i );
					cb.aaload();
					String methodName = access.isField() ? "writeByField" : "writeByMethod";
					cb.invokeinterface( CD_BULK_ACCESSOR, methodName,
							MethodTypeDesc.of( ConstantDescs.CD_void, ConstantDescs.CD_Object, ConstantDescs.CD_int, ConstantDescs.CD_Object ) );
				}

				cb.return_();
			} )
			);
		} );
	}

	private static void generateBulkAccessorFields(io.smallrye.classfile.ClassBuilder clb, int count) {
		for ( int i = 0; i < count; i++ ) {
			clb.withField( "accessor_" + i, CD_BULK_ACCESSOR, ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL );
		}
	}

	private static void generateBulkAccessorConstructor(io.smallrye.classfile.ClassBuilder clb, ClassDesc generatedDesc, int fieldCount) {
		ClassDesc[] paramTypes = new ClassDesc[fieldCount];
		java.util.Arrays.fill( paramTypes, CD_BULK_ACCESSOR );
		MethodTypeDesc ctorType = MethodTypeDesc.of( ConstantDescs.CD_void, paramTypes );

		clb.withMethod( CONSTRUCTOR_NAME, ctorType, ClassFile.ACC_PUBLIC, mb -> mb.withCode( cb -> {
			cb.aload( 0 );
			cb.invokespecial( ConstantDescs.CD_Object, CONSTRUCTOR_NAME, MTD_VOID_INIT );
			for ( int i = 0; i < fieldCount; i++ ) {
				cb.aload( 0 );
				cb.aload( i + 1 );
				cb.putfield( generatedDesc, "accessor_" + i, CD_BULK_ACCESSOR );
			}
			cb.return_();
		} ) );
	}

	private static void generateNoArgConstructor(io.smallrye.classfile.ClassBuilder clb) {
		clb.withMethod( CONSTRUCTOR_NAME, MTD_VOID_INIT, ClassFile.ACC_PUBLIC, mb -> mb.withCode( cb -> {
			cb.aload( 0 );
			cb.invokespecial( ConstantDescs.CD_Object, CONSTRUCTOR_NAME, MTD_VOID_INIT );
			cb.return_();
		} ) );
	}
}
