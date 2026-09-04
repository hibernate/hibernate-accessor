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
 * Emits a shared value-reader implementing {@code HibernateAccessorValueReader}, carrying
 * {@code (classIndex, memberIndex)} and dispatching {@code get()} via an outer
 * {@code tableswitch(classIndex)} into the matching entity's host {@code $$read*} method.
 *
 * <p>This is the whole-model half of the double-switch: a single class is shared by every accessor
 * in the model, so the {@code get()} call site stays monomorphic no matter how many entity types
 * exist. Mirrors {@code HibernateAccessorSingleImplGenerator#generateReaderImpl}, including the
 * class-switch chunking used past {@link GeneratedNames#SWITCH_CHUNK_SIZE} entity types.
 */
final class SwitchReaderEmitter {

	private SwitchReaderEmitter() {
	}

	/**
	 * @param packageInternal the model package in internal form
	 * @param simpleName      {@link GeneratedNames#READER_FIELD_SIMPLE} or {@link GeneratedNames#READER_METHOD_SIMPLE}
	 * @param hostReadMethod  the host method to dispatch into ({@code $$read} or {@code $$readMethod})
	 * @param entityCount     number of entity types (== class-switch width)
	 * @return the reader class bytes
	 */
	static byte[] emit(String packageInternal, String simpleName, String hostReadMethod, int entityCount) {
		String className = packageInternal + "/" + simpleName;
		ClassWriter cw = new ModelClassWriter();
		cw.visit(
				Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, className,
				"Ljava/lang/Object;L" + GeneratedNames.READER_INTERFACE_INTERNAL + "<Ljava/lang/Object;>;",
				"java/lang/Object", new String[] { GeneratedNames.READER_INTERFACE_INTERNAL } );

		cw.visitField( Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "classIndex", "I", null, null ).visitEnd();
		cw.visitField( Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "memberIndex", "I", null, null ).visitEnd();

		emitConstructor( cw, className );
		emitGet( cw, className, packageInternal, hostReadMethod, entityCount );

		cw.visitEnd();
		return cw.toByteArray();
	}

	private static void emitConstructor(ClassWriter cw, String className) {
		MethodVisitor mv = cw.visitMethod( Opcodes.ACC_PUBLIC, "<init>", "(II)V", null, null );
		mv.visitCode();
		mv.visitVarInsn( Opcodes.ALOAD, 0 );
		mv.visitMethodInsn( Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false );
		mv.visitVarInsn( Opcodes.ALOAD, 0 );
		mv.visitVarInsn( Opcodes.ILOAD, 1 );
		mv.visitFieldInsn( Opcodes.PUTFIELD, className, "classIndex", "I" );
		mv.visitVarInsn( Opcodes.ALOAD, 0 );
		mv.visitVarInsn( Opcodes.ILOAD, 2 );
		mv.visitFieldInsn( Opcodes.PUTFIELD, className, "memberIndex", "I" );
		mv.visitInsn( Opcodes.RETURN );
		mv.visitMaxs( 0, 0 );
		mv.visitEnd();
	}

	private static void emitGet(
			ClassWriter cw, String className, String packageInternal, String hostReadMethod, int entityCount) {
		if ( entityCount <= GeneratedNames.SWITCH_CHUNK_SIZE ) {
			MethodVisitor mv = cw.visitMethod(
					Opcodes.ACC_PUBLIC, "get", "(Ljava/lang/Object;)Ljava/lang/Object;", null, null );
			mv.visitCode();
			emitGetSwitch( mv, className, packageInternal, hostReadMethod, 0, entityCount );
			mv.visitMaxs( 0, 0 );
			mv.visitEnd();
			return;
		}

		int chunkCount = ( entityCount + GeneratedNames.SWITCH_CHUNK_SIZE - 1 ) / GeneratedNames.SWITCH_CHUNK_SIZE;
		for ( int chunk = 0; chunk < chunkCount; chunk++ ) {
			int start = chunk * GeneratedNames.SWITCH_CHUNK_SIZE;
			int end = Math.min( start + GeneratedNames.SWITCH_CHUNK_SIZE, entityCount );
			MethodVisitor mv = cw.visitMethod(
					Opcodes.ACC_PRIVATE, "get$" + chunk, "(Ljava/lang/Object;)Ljava/lang/Object;", null, null );
			mv.visitCode();
			emitGetSwitch( mv, className, packageInternal, hostReadMethod, start, end );
			mv.visitMaxs( 0, 0 );
			mv.visitEnd();
		}
		emitGetChunkDispatcher( cw, className, chunkCount );
	}

	// One tableswitch over class indices [startIndex, endIndex) -> INVOKESTATIC E_i.$$read*. Virtual, this in slot 0.
	private static void emitGetSwitch(
			MethodVisitor mv, String className, String packageInternal, String hostReadMethod, int startIndex,
			int endIndex) {
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
			mv.visitVarInsn( Opcodes.ALOAD, 0 );
			mv.visitFieldInsn( Opcodes.GETFIELD, className, "memberIndex", "I" );
			mv.visitVarInsn( Opcodes.ALOAD, 1 );
			mv.visitMethodInsn(
					Opcodes.INVOKESTATIC, packageInternal + "/E" + ( startIndex + i ), hostReadMethod,
					GeneratedNames.READ_METHOD_DESC, false );
			mv.visitInsn( Opcodes.ARETURN );
		}

		mv.visitLabel( defaultLabel );
		throwUnknownClassIndex( mv, className );
	}

	// Public get(): switch(classIndex / SWITCH_CHUNK_SIZE) -> INVOKEVIRTUAL this.get$i(Object). this in slot 0, arg in slot 1.
	private static void emitGetChunkDispatcher(ClassWriter cw, String className, int chunkCount) {
		MethodVisitor mv = cw.visitMethod(
				Opcodes.ACC_PUBLIC, "get", "(Ljava/lang/Object;)Ljava/lang/Object;", null, null );
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
			mv.visitMethodInsn(
					Opcodes.INVOKEVIRTUAL, className, "get$" + i, "(Ljava/lang/Object;)Ljava/lang/Object;", false );
			mv.visitInsn( Opcodes.ARETURN );
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
