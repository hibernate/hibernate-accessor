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
 * Emits a single benchmark entity class as bytecode.
 *
 * <p>Each entity {@code E<t>} carries:
 * <ul>
 *   <li>{@code fieldCount} private {@code int} fields {@code f0..f<n>} with {@code getF<i>}/{@code setF<i>};</li>
 *   <li>{@code depth} private reference fields {@code r0..r<d>} typed as sibling entities
 *       ({@code E_i.r_j -> E_((i+1+j) % entityCount)}) with {@code getR<j>}/{@code setR<j>};</li>
 *   <li>a public no-arg constructor;</li>
 *   <li>the double-switch host read methods {@code $$read}/{@code $$readMethod}
 *       ({@code (int memberIndex, Object) -> Object}), an inner {@code tableswitch(memberIndex)} that
 *       reads the member via direct field access resp. its getter and boxes primitives -- exactly the
 *       shape the Quarkus build-time transformer bakes into host entities.</li>
 * </ul>
 *
 * <p>The host methods are extra statics that the reflection/method-handle/lambda/ASM/ByteBuddy
 * strategies simply ignore, so every strategy shares this one superset entity copy. Only the
 * {@code GENERATED_DOUBLE_SWITCH} strategy dispatches into them (via a shared reader's outer
 * {@code tableswitch(classIndex)}).
 */
public final class EntityClassEmitter {

	private static final int CLASS_VERSION = Opcodes.V17;

	private EntityClassEmitter() {
	}

	/**
	 * @param packageInternal the package in internal form, e.g. {@code org/hibernate/.../generated/e8_f4_d2}
	 * @param typeIndex       this entity's index {@code t}
	 * @param fieldCount      number of scalar {@code int} fields
	 * @param depth           number of to-one reference fields
	 * @param entityCount     total number of types (for the reference wiring modulo)
	 * @return the class bytes for {@code E<typeIndex>}
	 */
	public static byte[] emit(String packageInternal, int typeIndex, int fieldCount, int depth, int entityCount) {
		String internalName = packageInternal + "/E" + typeIndex;

		ClassWriter cw = new ModelClassWriter();
		cw.visit( CLASS_VERSION, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internalName, null, "java/lang/Object", null );

		emitConstructor( cw, internalName );

		for ( int i = 0; i < fieldCount; i++ ) {
			emitScalar( cw, internalName, "f" + i );
		}
		for ( int j = 0; j < depth; j++ ) {
			emitReference( cw, internalName, "r" + j, leafDescriptor( packageInternal, typeIndex, j, entityCount ) );
		}

		emitReadMethod( cw, packageInternal, typeIndex, fieldCount, depth, entityCount, false );
		emitReadMethod( cw, packageInternal, typeIndex, fieldCount, depth, entityCount, true );

		cw.visitEnd();
		return cw.toByteArray();
	}

	private static String leafDescriptor(String packageInternal, int typeIndex, int refIndex, int entityCount) {
		int leafType = ( typeIndex + 1 + refIndex ) % entityCount;
		return "L" + packageInternal + "/E" + leafType + ";";
	}

	private static void emitConstructor(ClassWriter cw, String internalName) {
		MethodVisitor mv = cw.visitMethod( Opcodes.ACC_PUBLIC, "<init>", "()V", null, null );
		mv.visitCode();
		mv.visitVarInsn( Opcodes.ALOAD, 0 );
		mv.visitMethodInsn( Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false );
		mv.visitInsn( Opcodes.RETURN );
		mv.visitMaxs( 0, 0 );
		mv.visitEnd();
	}

	private static void emitScalar(ClassWriter cw, String owner, String name) {
		cw.visitField( Opcodes.ACC_PRIVATE, name, "I", null, null ).visitEnd();

		MethodVisitor getter = cw.visitMethod( Opcodes.ACC_PUBLIC, getterName( name ), "()I", null, null );
		getter.visitCode();
		getter.visitVarInsn( Opcodes.ALOAD, 0 );
		getter.visitFieldInsn( Opcodes.GETFIELD, owner, name, "I" );
		getter.visitInsn( Opcodes.IRETURN );
		getter.visitMaxs( 0, 0 );
		getter.visitEnd();

		MethodVisitor setter = cw.visitMethod( Opcodes.ACC_PUBLIC, setterName( name ), "(I)V", null, null );
		setter.visitCode();
		setter.visitVarInsn( Opcodes.ALOAD, 0 );
		setter.visitVarInsn( Opcodes.ILOAD, 1 );
		setter.visitFieldInsn( Opcodes.PUTFIELD, owner, name, "I" );
		setter.visitInsn( Opcodes.RETURN );
		setter.visitMaxs( 0, 0 );
		setter.visitEnd();
	}

	private static void emitReference(ClassWriter cw, String owner, String name, String descriptor) {
		cw.visitField( Opcodes.ACC_PRIVATE, name, descriptor, null, null ).visitEnd();

		MethodVisitor getter = cw.visitMethod( Opcodes.ACC_PUBLIC, getterName( name ), "()" + descriptor, null, null );
		getter.visitCode();
		getter.visitVarInsn( Opcodes.ALOAD, 0 );
		getter.visitFieldInsn( Opcodes.GETFIELD, owner, name, descriptor );
		getter.visitInsn( Opcodes.ARETURN );
		getter.visitMaxs( 0, 0 );
		getter.visitEnd();

		MethodVisitor setter = cw.visitMethod( Opcodes.ACC_PUBLIC, setterName( name ), "(" + descriptor + ")V", null, null );
		setter.visitCode();
		setter.visitVarInsn( Opcodes.ALOAD, 0 );
		setter.visitVarInsn( Opcodes.ALOAD, 1 );
		setter.visitFieldInsn( Opcodes.PUTFIELD, owner, name, descriptor );
		setter.visitInsn( Opcodes.RETURN );
		setter.visitMaxs( 0, 0 );
		setter.visitEnd();
	}

	/**
	 * Emits {@code $$read}/{@code $$readMethod} reading each member (fields first, then references) and
	 * boxing scalars, mirroring the Quarkus host read switch.
	 *
	 * <p>When the member count exceeds {@link GeneratedNames#SWITCH_CHUNK_SIZE} the switch is split, as
	 * in {@code HibernateAccessorHostClassFunction}: each {@code SWITCH_CHUNK_SIZE}-wide slice becomes a
	 * static {@code <name>$<chunk>} method holding a {@code tableswitch} over its own index range, and
	 * the public {@code <name>} method dispatches to it via {@code switch(memberIndex / SWITCH_CHUNK_SIZE)}.
	 */
	private static void emitReadMethod(
			ClassWriter cw, String packageInternal, int typeIndex, int fieldCount, int depth, int entityCount,
			boolean useGetter) {
		String owner = packageInternal + "/E" + typeIndex;
		String methodName = useGetter ? GeneratedNames.READ_METHOD_GETTER : GeneratedNames.READ_METHOD_FIELD;
		int memberCount = fieldCount + depth;

		if ( memberCount <= GeneratedNames.SWITCH_CHUNK_SIZE ) {
			emitReadSwitch( cw, owner, packageInternal, typeIndex, fieldCount, entityCount, useGetter, methodName, 0, memberCount );
			return;
		}

		int chunkCount = ( memberCount + GeneratedNames.SWITCH_CHUNK_SIZE - 1 ) / GeneratedNames.SWITCH_CHUNK_SIZE;
		for ( int chunk = 0; chunk < chunkCount; chunk++ ) {
			int start = chunk * GeneratedNames.SWITCH_CHUNK_SIZE;
			int end = Math.min( start + GeneratedNames.SWITCH_CHUNK_SIZE, memberCount );
			emitReadSwitch(
					cw, owner, packageInternal, typeIndex, fieldCount, entityCount, useGetter,
					methodName + "$" + chunk, start, end );
		}
		emitReadChunkDispatcher( cw, owner, methodName, chunkCount );
	}

	// One tableswitch method over member indices [startIndex, endIndex). Static, args in slots 0/1.
	private static void emitReadSwitch(
			ClassWriter cw, String owner, String packageInternal, int typeIndex, int fieldCount, int entityCount,
			boolean useGetter, String methodName, int startIndex, int endIndex) {
		MethodVisitor mv = cw.visitMethod(
				Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, methodName, GeneratedNames.READ_METHOD_DESC, null, null );
		mv.visitCode();

		int count = endIndex - startIndex;
		Label[] labels = new Label[count];
		for ( int i = 0; i < count; i++ ) {
			labels[i] = new Label();
		}
		Label defaultLabel = new Label();

		mv.visitVarInsn( Opcodes.ILOAD, 0 );
		mv.visitTableSwitchInsn( startIndex, endIndex - 1, defaultLabel, labels );

		for ( int i = 0; i < count; i++ ) {
			int memberIndex = startIndex + i;
			mv.visitLabel( labels[i] );
			mv.visitVarInsn( Opcodes.ALOAD, 1 );
			mv.visitTypeInsn( Opcodes.CHECKCAST, owner );

			if ( memberIndex < fieldCount ) {
				String field = "f" + memberIndex;
				if ( useGetter ) {
					mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, owner, getterName( field ), "()I", false );
				}
				else {
					mv.visitFieldInsn( Opcodes.GETFIELD, owner, field, "I" );
				}
				mv.visitMethodInsn(
						Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false );
			}
			else {
				int refIndex = memberIndex - fieldCount;
				String field = "r" + refIndex;
				String descriptor = leafDescriptor( packageInternal, typeIndex, refIndex, entityCount );
				if ( useGetter ) {
					mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, owner, getterName( field ), "()" + descriptor, false );
				}
				else {
					mv.visitFieldInsn( Opcodes.GETFIELD, owner, field, descriptor );
				}
			}
			mv.visitInsn( Opcodes.ARETURN );
		}

		mv.visitLabel( defaultLabel );
		throwUnknownMemberIndex( mv, owner );

		mv.visitMaxs( 0, 0 );
		mv.visitEnd();
	}

	// Static dispatcher: switch(memberIndex / SWITCH_CHUNK_SIZE) -> INVOKESTATIC the matching chunk method.
	private static void emitReadChunkDispatcher(ClassWriter cw, String owner, String methodName, int chunkCount) {
		MethodVisitor mv = cw.visitMethod(
				Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, methodName, GeneratedNames.READ_METHOD_DESC, null, null );
		mv.visitCode();

		Label[] labels = new Label[chunkCount];
		for ( int i = 0; i < chunkCount; i++ ) {
			labels[i] = new Label();
		}
		Label defaultLabel = new Label();

		mv.visitVarInsn( Opcodes.ILOAD, 0 );
		AsmSupport.pushIntConst( mv, GeneratedNames.SWITCH_CHUNK_SIZE );
		mv.visitInsn( Opcodes.IDIV );
		mv.visitTableSwitchInsn( 0, chunkCount - 1, defaultLabel, labels );

		for ( int i = 0; i < chunkCount; i++ ) {
			mv.visitLabel( labels[i] );
			mv.visitVarInsn( Opcodes.ILOAD, 0 );
			mv.visitVarInsn( Opcodes.ALOAD, 1 );
			mv.visitMethodInsn(
					Opcodes.INVOKESTATIC, owner, methodName + "$" + i, GeneratedNames.READ_METHOD_DESC, false );
			mv.visitInsn( Opcodes.ARETURN );
		}

		mv.visitLabel( defaultLabel );
		throwUnknownMemberIndex( mv, owner );

		mv.visitMaxs( 0, 0 );
		mv.visitEnd();
	}

	private static void throwUnknownMemberIndex(MethodVisitor mv, String owner) {
		mv.visitTypeInsn( Opcodes.NEW, "java/lang/IllegalArgumentException" );
		mv.visitInsn( Opcodes.DUP );
		mv.visitTypeInsn( Opcodes.NEW, "java/lang/StringBuilder" );
		mv.visitInsn( Opcodes.DUP );
		mv.visitLdcInsn( "Unknown member index " );
		mv.visitMethodInsn(
				Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false );
		mv.visitVarInsn( Opcodes.ILOAD, 0 );
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false );
		mv.visitLdcInsn( " for " + owner.replace( '/', '.' ) );
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
				"(Ljava/lang/String;)Ljava/lang/StringBuilder;", false );
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false );
		mv.visitMethodInsn(
				Opcodes.INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V", false );
		mv.visitInsn( Opcodes.ATHROW );
	}

	private static String getterName(String field) {
		return "get" + Character.toUpperCase( field.charAt( 0 ) ) + field.substring( 1 );
	}

	private static String setterName(String field) {
		return "set" + Character.toUpperCase( field.charAt( 0 ) ) + field.substring( 1 );
	}
}
