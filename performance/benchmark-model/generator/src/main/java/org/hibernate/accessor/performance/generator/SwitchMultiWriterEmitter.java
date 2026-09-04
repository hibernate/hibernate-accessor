/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.generator;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Emits a shared multi-value-writer implementing {@code HibernateAccessorMultiValueWriter}, carrying
 * a {@code classIndex} and dispatching {@code set()} via {@code tableswitch(classIndex)} into the
 * matching entity's host {@code $$writeAll*} method, which writes all scalar fields in one shot.
 *
 * <p>Mirrors {@link SwitchMultiReaderEmitter} on the write side.
 */
final class SwitchMultiWriterEmitter {

	private SwitchMultiWriterEmitter() {
	}

	/**
	 * @param packageInternal the model package in internal form
	 * @param simpleName      e.g. {@link GeneratedNames#MULTI_WRITER_FIELD_SIMPLE}
	 * @param hostWriteMethod the host method to dispatch into ({@code $$writeAll} or {@code $$writeAllMethod})
	 * @param entityCount     number of entity types (== class-switch width)
	 * @return the multi-writer class bytes
	 */
	static byte[] emit(String packageInternal, String simpleName, String hostWriteMethod, int entityCount) {
		String className = packageInternal + "/" + simpleName;
		ClassWriter cw = new ModelClassWriter();
		cw.visit(
				Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, className, null,
				"java/lang/Object", new String[] { GeneratedNames.MULTI_WRITER_INTERFACE_INTERNAL } );

		cw.visitField( Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "classIndex", "I", null, null ).visitEnd();

		emitConstructor( cw, className );
		emitSet( cw, className, packageInternal, hostWriteMethod, entityCount );

		cw.visitEnd();
		return cw.toByteArray();
	}

	private static void emitConstructor(ClassWriter cw, String className) {
		MethodVisitor mv = cw.visitMethod( Opcodes.ACC_PUBLIC, "<init>", "(I)V", null, null );
		mv.visitCode();
		mv.visitVarInsn( Opcodes.ALOAD, 0 );
		mv.visitMethodInsn( Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false );
		mv.visitVarInsn( Opcodes.ALOAD, 0 );
		mv.visitVarInsn( Opcodes.ILOAD, 1 );
		mv.visitFieldInsn( Opcodes.PUTFIELD, className, "classIndex", "I" );
		mv.visitInsn( Opcodes.RETURN );
		mv.visitMaxs( 0, 0 );
		mv.visitEnd();
	}

	private static void emitSet(
			ClassWriter cw, String className, String packageInternal, String hostWriteMethod, int entityCount) {
		if ( entityCount <= GeneratedNames.SWITCH_CHUNK_SIZE ) {
			MethodVisitor mv = cw.visitMethod(
					Opcodes.ACC_PUBLIC, "set", "(Ljava/lang/Object;[Ljava/lang/Object;)V", null, null );
			mv.visitCode();
			emitSetSwitch( mv, className, packageInternal, hostWriteMethod, 0, entityCount );
			mv.visitMaxs( 0, 0 );
			mv.visitEnd();
			return;
		}

		int chunkCount = ( entityCount + GeneratedNames.SWITCH_CHUNK_SIZE - 1 ) / GeneratedNames.SWITCH_CHUNK_SIZE;
		for ( int chunk = 0; chunk < chunkCount; chunk++ ) {
			int start = chunk * GeneratedNames.SWITCH_CHUNK_SIZE;
			int end = Math.min( start + GeneratedNames.SWITCH_CHUNK_SIZE, entityCount );
			MethodVisitor mv = cw.visitMethod(
					Opcodes.ACC_PRIVATE, "set$" + chunk, "(Ljava/lang/Object;[Ljava/lang/Object;)V", null, null );
			mv.visitCode();
			emitSetSwitch( mv, className, packageInternal, hostWriteMethod, start, end );
			mv.visitMaxs( 0, 0 );
			mv.visitEnd();
		}
		emitSetChunkDispatcher( cw, className, chunkCount );
	}

	// slot 0 = this, slot 1 = instance, slot 2 = values
	private static void emitSetSwitch(
			MethodVisitor mv, String className, String packageInternal, String hostWriteMethod,
			int startIndex, int endIndex) {
		int count = endIndex - startIndex;
		Label[] labels = new Label[count];
		for ( int i = 0; i < count; i++ ) {
			labels[i] = new Label();
		}
		Label defaultLabel = new Label();

		mv.visitVarInsn( Opcodes.ALOAD, 0 );
		mv.visitFieldInsn( Opcodes.GETFIELD, className, "classIndex", "I" );
		mv.visitTableSwitchInsn( startIndex, endIndex - 1, defaultLabel, labels );

		for ( int i = 0; i < count; i++ ) {
			mv.visitLabel( labels[i] );
			mv.visitVarInsn( Opcodes.ALOAD, 1 );
			mv.visitVarInsn( Opcodes.ALOAD, 2 );
			mv.visitMethodInsn(
					Opcodes.INVOKESTATIC, packageInternal + "/E" + ( startIndex + i ), hostWriteMethod,
					GeneratedNames.WRITE_ALL_METHOD_DESC, false );
			mv.visitInsn( Opcodes.RETURN );
		}

		mv.visitLabel( defaultLabel );
		throwUnknownClassIndex( mv, className );
	}

	private static void emitSetChunkDispatcher(ClassWriter cw, String className, int chunkCount) {
		MethodVisitor mv = cw.visitMethod(
				Opcodes.ACC_PUBLIC, "set", "(Ljava/lang/Object;[Ljava/lang/Object;)V", null, null );
		mv.visitCode();

		Label[] labels = new Label[chunkCount];
		for ( int i = 0; i < chunkCount; i++ ) {
			labels[i] = new Label();
		}
		Label defaultLabel = new Label();

		mv.visitVarInsn( Opcodes.ALOAD, 0 );
		mv.visitFieldInsn( Opcodes.GETFIELD, className, "classIndex", "I" );
		AsmSupport.pushIntConst( mv, GeneratedNames.SWITCH_CHUNK_SIZE );
		mv.visitInsn( Opcodes.IDIV );
		mv.visitTableSwitchInsn( 0, chunkCount - 1, defaultLabel, labels );

		for ( int i = 0; i < chunkCount; i++ ) {
			mv.visitLabel( labels[i] );
			mv.visitVarInsn( Opcodes.ALOAD, 0 );
			mv.visitVarInsn( Opcodes.ALOAD, 1 );
			mv.visitVarInsn( Opcodes.ALOAD, 2 );
			mv.visitMethodInsn(
					Opcodes.INVOKEVIRTUAL, className, "set$" + i, "(Ljava/lang/Object;[Ljava/lang/Object;)V", false );
			mv.visitInsn( Opcodes.RETURN );
		}

		mv.visitLabel( defaultLabel );
		throwUnknownClassIndex( mv, className );

		mv.visitMaxs( 0, 0 );
		mv.visitEnd();
	}

	private static void throwUnknownClassIndex(MethodVisitor mv, String className) {
		mv.visitTypeInsn( Opcodes.NEW, "java/lang/IllegalArgumentException" );
		mv.visitInsn( Opcodes.DUP );
		mv.visitTypeInsn( Opcodes.NEW, "java/lang/StringBuilder" );
		mv.visitInsn( Opcodes.DUP );
		mv.visitLdcInsn( "Unknown class index " );
		mv.visitMethodInsn(
				Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false );
		mv.visitVarInsn( Opcodes.ALOAD, 0 );
		mv.visitFieldInsn( Opcodes.GETFIELD, className, "classIndex", "I" );
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false );
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false );
		mv.visitMethodInsn(
				Opcodes.INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V", false );
		mv.visitInsn( Opcodes.ATHROW );
	}
}
