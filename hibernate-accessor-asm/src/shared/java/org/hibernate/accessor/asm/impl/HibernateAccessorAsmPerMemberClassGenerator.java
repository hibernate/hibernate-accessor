/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.asm.impl;

import static org.hibernate.accessor.asm.impl.HibernateAccessorAsmUtils.emitBox;
import static org.hibernate.accessor.asm.impl.HibernateAccessorAsmUtils.emitWideningUnbox;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import org.hibernate.accessor.HibernateAccessorValueReader;
import org.hibernate.accessor.HibernateAccessorValueWriter;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Generates one dedicated class per member for the {@link org.hibernate.accessor.asm.HibernateAccessorAsmGenerationStrategy#PER_MEMBER PER_MEMBER}
 * strategy: the reader's {@code get}/writer's {@code set} body is a single direct field access or
 * method call, with no {@code TABLESWITCH} and no shared bulk accessor.
 *
 * <p>Like the single-class multi-value generator, the produced class is defined as a nestmate of the
 * target class, so the emitted {@code GETFIELD}/{@code PUTFIELD}/{@code INVOKE*} reach even
 * {@code private} members directly. The boxing/widening semantics mirror the bulk accessor
 * ({@code emitBox} on reads, {@code emitWideningUnbox} on writes) so a member behaves identically
 * regardless of the configured strategy.
 */
final class HibernateAccessorAsmPerMemberClassGenerator implements Opcodes {

	private static final String READER_INTERNAL = Type.getInternalName( HibernateAccessorValueReader.class );
	private static final String WRITER_INTERNAL = Type.getInternalName( HibernateAccessorValueWriter.class );

	private HibernateAccessorAsmPerMemberClassGenerator() {
	}

	// get(Object instance) -> Object
	// locals: [this=0, instance=1]
	static byte[] generateReader(Member member) {
		final Class<?> targetClass = member.getDeclaringClass();
		final String targetInternal = Type.getInternalName( targetClass );
		final String generatedName = targetInternal + "$$HibernateAccessorReader_" + member.getName();

		ClassWriter cw = new ClassWriter( ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS );
		cw.visit( V17, ACC_PUBLIC | ACC_SUPER | ACC_SYNTHETIC, generatedName, null, "java/lang/Object", new String[] { READER_INTERNAL } );

		generateConstructor( cw );

		MethodVisitor mv = cw.visitMethod( ACC_PUBLIC, "get", "(Ljava/lang/Object;)Ljava/lang/Object;", null, null );
		mv.visitCode();
		mv.visitVarInsn( ALOAD, 1 );
		mv.visitTypeInsn( CHECKCAST, targetInternal );
		if ( member instanceof Field field ) {
			mv.visitFieldInsn( GETFIELD, targetInternal, field.getName(), Type.getDescriptor( field.getType() ) );
			emitBox( mv, field.getType() );
		}
		else {
			Method method = (Method) member;
			boolean isInterface = targetClass.isInterface();
			mv.visitMethodInsn( isInterface ? INVOKEINTERFACE : INVOKEVIRTUAL, targetInternal, method.getName(), Type.getMethodDescriptor( method ), isInterface );
			emitBox( mv, method.getReturnType() );
		}
		mv.visitInsn( ARETURN );
		mv.visitMaxs( 0, 0 );
		mv.visitEnd();

		cw.visitEnd();
		return cw.toByteArray();
	}

	// set(Object instance, Object value) -> void
	// locals: [this=0, instance=1, value=2]
	static byte[] generateWriter(Member member) {
		final Class<?> targetClass = member.getDeclaringClass();
		final String targetInternal = Type.getInternalName( targetClass );
		final String generatedName = targetInternal + "$$HibernateAccessorWriter_" + member.getName();

		ClassWriter cw = new ClassWriter( ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS );
		cw.visit( V17, ACC_PUBLIC | ACC_SUPER | ACC_SYNTHETIC, generatedName, null, "java/lang/Object", new String[] { WRITER_INTERNAL } );

		generateConstructor( cw );

		MethodVisitor mv = cw.visitMethod( ACC_PUBLIC, "set", "(Ljava/lang/Object;Ljava/lang/Object;)V", null, null );
		mv.visitCode();
		mv.visitVarInsn( ALOAD, 1 );
		mv.visitTypeInsn( CHECKCAST, targetInternal );
		mv.visitVarInsn( ALOAD, 2 );
		if ( member instanceof Field field ) {
			emitWideningUnbox( mv, field.getType() );
			mv.visitFieldInsn( PUTFIELD, targetInternal, field.getName(), Type.getDescriptor( field.getType() ) );
		}
		else {
			Method method = (Method) member;
			boolean isInterface = targetClass.isInterface();
			emitWideningUnbox( mv, method.getParameterTypes()[0] );
			mv.visitMethodInsn( isInterface ? INVOKEINTERFACE : INVOKEVIRTUAL, targetInternal, method.getName(), Type.getMethodDescriptor( method ), isInterface );
			if ( method.getReturnType() != void.class ) {
				Type retType = Type.getType( method.getReturnType() );
				mv.visitInsn( retType.getSize() == 2 ? POP2 : POP );
			}
		}
		mv.visitInsn( RETURN );
		mv.visitMaxs( 0, 0 );
		mv.visitEnd();

		cw.visitEnd();
		return cw.toByteArray();
	}

	private static void generateConstructor(ClassWriter cw) {
		MethodVisitor mv = cw.visitMethod( ACC_PUBLIC, "<init>", "()V", null, null );
		mv.visitCode();
		mv.visitVarInsn( ALOAD, 0 );
		mv.visitMethodInsn( INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false );
		mv.visitInsn( RETURN );
		mv.visitMaxs( 0, 0 );
		mv.visitEnd();
	}
}
