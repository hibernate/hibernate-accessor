package org.hibernate.accessor.classfile.impl;

import static org.hibernate.accessor.classfile.impl.ClassFileUtils.CONSTRUCTOR_NAME;
import static org.hibernate.accessor.classfile.impl.ClassFileUtils.GENERATED_CLASS_MAJOR_VERSION;
import static org.hibernate.accessor.classfile.impl.ClassFileUtils.classDesc;
import static org.hibernate.accessor.classfile.impl.ClassFileUtils.emitBox;
import static org.hibernate.accessor.classfile.impl.ClassFileUtils.emitWideningUnbox;
import static org.hibernate.accessor.classfile.impl.ClassFileUtils.generatedClassDesc;
import static org.hibernate.accessor.classfile.impl.ClassFileUtils.methodTypeDesc;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.ValueWriter;

import io.smallrye.classfile.ClassFile;

final class ClassFilePerMemberClassGenerator {

	private static final ClassDesc CD_READER = classDesc( ValueReader.class );
	private static final ClassDesc CD_WRITER = classDesc( ValueWriter.class );
	private static final MethodTypeDesc MTD_VOID_INIT = methodTypeDesc( ConstantDescs.CD_void );

	static byte[] generateReader(Member member) {
		String generatedInternalName = member.getDeclaringClass().getName().replace( '.', '/' ) + "$$HibernateAccessorReader";
		ClassDesc generatedDesc = generatedClassDesc( generatedInternalName );

		return ClassFile.of().build( generatedDesc, clb -> {
			clb.with( GENERATED_CLASS_MAJOR_VERSION );
			clb.withFlags( ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER | ClassFile.ACC_SYNTHETIC );
			clb.withSuperclass( ConstantDescs.CD_Object );
			clb.withInterfaceSymbols( CD_READER );

			generateNoArgConstructor( clb );

			clb.withMethod( "get", MethodTypeDesc.of( ConstantDescs.CD_Object, ConstantDescs.CD_Object ), ClassFile.ACC_PUBLIC, mb -> mb.withCode( cb -> {
				cb.aload( 1 );
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

				cb.areturn();
			} )
			);
		} );
	}

	static byte[] generateWriter(Member member) {
		String generatedInternalName = member.getDeclaringClass().getName().replace( '.', '/' ) + "$$HibernateAccessorWriter";
		ClassDesc generatedDesc = generatedClassDesc( generatedInternalName );

		return ClassFile.of().build( generatedDesc, clb -> {
			clb.with( GENERATED_CLASS_MAJOR_VERSION );
			clb.withFlags( ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER | ClassFile.ACC_SYNTHETIC );
			clb.withSuperclass( ConstantDescs.CD_Object );
			clb.withInterfaceSymbols( CD_WRITER );

			generateNoArgConstructor( clb );

			clb.withMethod( "set", MethodTypeDesc.of( ConstantDescs.CD_void, ConstantDescs.CD_Object, ConstantDescs.CD_Object ), ClassFile.ACC_PUBLIC, mb -> mb.withCode( cb -> {
				cb.aload( 1 );
				if ( member instanceof Field field ) {
					ClassDesc ownerDesc = classDesc( field.getDeclaringClass() );
					cb.checkcast( ownerDesc );
					cb.aload( 2 );
					emitWideningUnbox( cb, field.getType() );
					cb.putfield( ownerDesc, field.getName(), classDesc( field.getType() ) );
				}
				else {
					Method method = (Method) member;
					ClassDesc ownerDesc = classDesc( method.getDeclaringClass() );
					boolean isInterface = method.getDeclaringClass().isInterface();
					cb.checkcast( ownerDesc );
					cb.aload( 2 );
					emitWideningUnbox( cb, method.getParameterTypes()[0] );
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
				cb.return_();
			} )
			);
		} );
	}

	private static void generateNoArgConstructor(io.smallrye.classfile.ClassBuilder clb) {
		clb.withMethod( CONSTRUCTOR_NAME, MTD_VOID_INIT, ClassFile.ACC_PUBLIC, mb -> mb.withCode( cb -> {
			cb.aload( 0 );
			cb.invokespecial( ConstantDescs.CD_Object, CONSTRUCTOR_NAME, MTD_VOID_INIT );
			cb.return_();
		} ) );
	}
}
