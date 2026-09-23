/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.classfile.impl;

import static org.hibernate.accessor.classfile.impl.ClassFileUtils.CONSTRUCTOR_NAME;
import static org.hibernate.accessor.classfile.impl.ClassFileUtils.GENERATED_CLASS_MAJOR_VERSION;
import static org.hibernate.accessor.classfile.impl.ClassFileUtils.classDesc;
import static org.hibernate.accessor.classfile.impl.ClassFileUtils.constructorTypeDesc;
import static org.hibernate.accessor.classfile.impl.ClassFileUtils.emitBox;
import static org.hibernate.accessor.classfile.impl.ClassFileUtils.emitIntConstant;
import static org.hibernate.accessor.classfile.impl.ClassFileUtils.emitThrow;
import static org.hibernate.accessor.classfile.impl.ClassFileUtils.emitWideningUnbox;
import static org.hibernate.accessor.classfile.impl.ClassFileUtils.generatedClassDesc;
import static org.hibernate.accessor.classfile.impl.ClassFileUtils.methodTypeDesc;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.accessor.classfile.spi.ClassFileBulkAccessor;

import io.smallrye.classfile.ClassFile;
import io.smallrye.classfile.CodeBuilder;
import io.smallrye.classfile.Label;
import io.smallrye.classfile.instruction.SwitchCase;

final class ClassFileBulkAccessorClassGenerator {

	private static final ClassDesc CD_BULK_ACCESSOR = classDesc( ClassFileBulkAccessor.class );
	private static final MethodTypeDesc MTD_READ = MethodTypeDesc.of( ConstantDescs.CD_Object, ConstantDescs.CD_Object, ConstantDescs.CD_int );
	private static final MethodTypeDesc MTD_WRITE = MethodTypeDesc.of( ConstantDescs.CD_void, ConstantDescs.CD_Object, ConstantDescs.CD_int, ConstantDescs.CD_Object );
	private static final MethodTypeDesc MTD_NEW_INSTANCE = MethodTypeDesc.of( ConstantDescs.CD_Object, ConstantDescs.CD_int, ConstantDescs.CD_Object.arrayType() );
	private static final MethodTypeDesc MTD_VOID_INIT = methodTypeDesc( ConstantDescs.CD_void );

	static byte[] generate(Class<?> targetClass, Field[] fields, Method[] getterMethods, Method[] setterMethods, Constructor<?>[] constructors) {
		ClassDesc targetDesc = classDesc( targetClass );
		boolean isInterface = targetClass.isInterface();
		String generatedInternalName = targetClass.getName().replace( '.', '/' ) + "$$HibernateAccessor";
		ClassDesc generatedDesc = generatedClassDesc( generatedInternalName );

		return ClassFile.of().build( generatedDesc, clb -> {
			clb.with( GENERATED_CLASS_MAJOR_VERSION );
			clb.withFlags( ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER | ClassFile.ACC_SYNTHETIC );
			clb.withSuperclass( ConstantDescs.CD_Object );
			clb.withInterfaceSymbols( CD_BULK_ACCESSOR );

			generateConstructor( clb );
			generateReadByField( clb, targetDesc, fields );
			generateWriteByField( clb, targetDesc, fields );
			generateReadByMethod( clb, targetDesc, isInterface, getterMethods );
			generateWriteByMethod( clb, targetDesc, isInterface, setterMethods );
			generateNewInstance( clb, targetDesc, constructors );
		} );
	}

	private static void generateConstructor(io.smallrye.classfile.ClassBuilder clb) {
		clb.withMethod( CONSTRUCTOR_NAME, MTD_VOID_INIT, ClassFile.ACC_PUBLIC, mb -> mb.withCode( cb -> {
			cb.aload( 0 );
			cb.invokespecial( ConstantDescs.CD_Object, CONSTRUCTOR_NAME, MTD_VOID_INIT );
			cb.return_();
		} ) );
	}

	private static void generateReadByField(io.smallrye.classfile.ClassBuilder clb, ClassDesc targetDesc, Field[] fields) {
		clb.withMethod( "readByField", MTD_READ, ClassFile.ACC_PUBLIC, mb -> mb.withCode( cb -> {
			if ( fields.length == 0 ) {
				emitThrow( cb, "No fields declared" );
			}
			else {
				generateTableSwitch( cb, 2, fields.length, i -> {
					cb.aload( 1 );
					cb.checkcast( targetDesc );
					Field f = fields[i];
					cb.getfield( targetDesc, f.getName(), classDesc( f.getType() ) );
					emitBox( cb, f.getType() );
					cb.areturn();
				}, () -> emitThrow( cb, "Invalid field index" ) );
			}
		} )
		);
	}

	private static void generateWriteByField(io.smallrye.classfile.ClassBuilder clb, ClassDesc targetDesc, Field[] fields) {
		clb.withMethod( "writeByField", MTD_WRITE, ClassFile.ACC_PUBLIC, mb -> mb.withCode( cb -> {
			if ( fields.length == 0 ) {
				emitThrow( cb, "No fields declared" );
			}
			else {
				generateTableSwitch( cb, 2, fields.length, i -> {
					cb.aload( 1 );
					cb.checkcast( targetDesc );
					Field f = fields[i];
					cb.aload( 3 );
					emitWideningUnbox( cb, f.getType() );
					cb.putfield( targetDesc, f.getName(), classDesc( f.getType() ) );
					cb.return_();
				}, () -> emitThrow( cb, "Invalid field index" ) );
			}
		} )
		);
	}

	private static void generateReadByMethod(io.smallrye.classfile.ClassBuilder clb, ClassDesc targetDesc, boolean isInterface, Method[] methods) {
		clb.withMethod( "readByMethod", MTD_READ, ClassFile.ACC_PUBLIC, mb -> mb.withCode( cb -> {
			if ( methods.length == 0 ) {
				emitThrow( cb, "No methods declared" );
			}
			else {
				generateTableSwitch( cb, 2, methods.length, i -> {
					Method m = methods[i];
					cb.aload( 1 );
					cb.checkcast( targetDesc );
					if ( isInterface ) {
						cb.invokeinterface( targetDesc, m.getName(), methodTypeDesc( m ) );
					}
					else {
						cb.invokevirtual( targetDesc, m.getName(), methodTypeDesc( m ) );
					}
					emitBox( cb, m.getReturnType() );
					cb.areturn();
				}, () -> emitThrow( cb, "Invalid method index" ) );
			}
		} )
		);
	}

	private static void generateWriteByMethod(io.smallrye.classfile.ClassBuilder clb, ClassDesc targetDesc, boolean isInterface, Method[] methods) {
		clb.withMethod( "writeByMethod", MTD_WRITE, ClassFile.ACC_PUBLIC, mb -> mb.withCode( cb -> {
			if ( methods.length == 0 ) {
				emitThrow( cb, "No methods declared" );
			}
			else {
				generateTableSwitch( cb, 2, methods.length, i -> {
					Method m = methods[i];
					cb.aload( 1 );
					cb.checkcast( targetDesc );
					cb.aload( 3 );
					emitWideningUnbox( cb, m.getParameterTypes()[0] );
					if ( isInterface ) {
						cb.invokeinterface( targetDesc, m.getName(), methodTypeDesc( m ) );
					}
					else {
						cb.invokevirtual( targetDesc, m.getName(), methodTypeDesc( m ) );
					}
					if ( m.getReturnType() != void.class ) {
						if ( m.getReturnType() == long.class || m.getReturnType() == double.class ) {
							cb.pop2();
						}
						else {
							cb.pop();
						}
					}
					cb.return_();
				}, () -> emitThrow( cb, "Invalid method index" ) );
			}
		} )
		);
	}

	private static void generateNewInstance(io.smallrye.classfile.ClassBuilder clb, ClassDesc targetDesc, Constructor<?>[] constructors) {
		clb.withMethod( "newInstance", MTD_NEW_INSTANCE, ClassFile.ACC_PUBLIC, mb -> mb.withCode( cb -> {
			if ( constructors.length == 0 ) {
				emitThrow( cb, "No constructors declared" );
			}
			else {
				generateTableSwitch( cb, 1, constructors.length, i -> {
					Constructor<?> ctor = constructors[i];
					cb.new_( targetDesc );
					cb.dup();
					Class<?>[] paramTypes = ctor.getParameterTypes();
					for ( int j = 0; j < paramTypes.length; j++ ) {
						cb.aload( 2 );
						emitIntConstant( cb, j );
						cb.aaload();
						emitWideningUnbox( cb, paramTypes[j] );
					}
					cb.invokespecial( targetDesc, CONSTRUCTOR_NAME, constructorTypeDesc( ctor ) );
					cb.areturn();
				}, () -> emitThrow( cb, "Invalid constructor index" ) );
			}
		} )
		);
	}

	private static void generateTableSwitch(CodeBuilder cb, int indexSlot, int count, java.util.function.IntConsumer caseEmitter, Runnable defaultEmitter) {
		Label[] caseLabels = new Label[count];
		List<SwitchCase> switchCases = new ArrayList<>( count );
		for ( int i = 0; i < count; i++ ) {
			caseLabels[i] = cb.newLabel();
			switchCases.add( SwitchCase.of( i, caseLabels[i] ) );
		}
		Label defaultLabel = cb.newLabel();

		cb.iload( indexSlot );
		cb.tableswitch( defaultLabel, switchCases );

		for ( int i = 0; i < count; i++ ) {
			cb.labelBinding( caseLabels[i] );
			caseEmitter.accept( i );
		}

		cb.labelBinding( defaultLabel );
		defaultEmitter.run();
	}
}
