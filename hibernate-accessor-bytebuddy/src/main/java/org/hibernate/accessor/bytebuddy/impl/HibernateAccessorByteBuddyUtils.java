/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.bytebuddy.impl;

import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;

import java.util.List;
import java.util.Map;

final class HibernateAccessorByteBuddyUtils {
	private static final String EXCEPTION_INTERNAL = "org/hibernate/accessor/HibernateAccessorException";

	private static final Map<Class<?>, BoxingInfo> BOXING = Map.of(
			boolean.class, new BoxingInfo( "java/lang/Boolean", "Z", "booleanValue", "()Z" ),
			byte.class, new BoxingInfo( "java/lang/Byte", "B", "byteValue", "()B" ),
			char.class, new BoxingInfo( "java/lang/Character", "C", "charValue", "()C" ),
			short.class, new BoxingInfo( "java/lang/Short", "S", "shortValue", "()S" ),
			int.class, new BoxingInfo( "java/lang/Integer", "I", "intValue", "()I" ),
			long.class, new BoxingInfo( "java/lang/Long", "J", "longValue", "()J" ),
			float.class, new BoxingInfo( "java/lang/Float", "F", "floatValue", "()F" ),
			double.class, new BoxingInfo( "java/lang/Double", "D", "doubleValue", "()D" )
	);

	// For each numeric primitive target that admits a widening conversion, the primitive
	// source types (strictly smaller, i.e. excluding the identity) that widen to it per
	// JLS 5.1.2. Targets with no widening source (boolean, byte, char) are absent.
	private static final Map<Class<?>, List<Class<?>>> WIDENING_SOURCES = Map.of(
			short.class, List.of( byte.class ),
			int.class, List.of( byte.class, short.class, char.class ),
			long.class, List.of( byte.class, short.class, char.class, int.class ),
			float.class, List.of( byte.class, short.class, char.class, int.class, long.class ),
			double.class, List.of( byte.class, short.class, char.class, int.class, long.class, float.class )
	);

	private HibernateAccessorByteBuddyUtils() {
	}

	static void emitBox(MethodVisitor mv, Class<?> type) {
		BoxingInfo info = BOXING.get( type );
		if ( info != null ) {
			mv.visitMethodInsn( Opcodes.INVOKESTATIC, info.wrapperInternal, "valueOf", info.valueOfDesc(), false );
		}
	}

	static void emitUnboxOrCast(MethodVisitor mv, Class<?> type) {
		BoxingInfo info = BOXING.get( type );
		if ( info != null ) {
			mv.visitTypeInsn( Opcodes.CHECKCAST, info.wrapperInternal );
			mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, info.wrapperInternal, info.unboxMethod, info.unboxDesc, false );
		}
		else {
			mv.visitTypeInsn( Opcodes.CHECKCAST, Type.getInternalName( type ) );
		}
	}

	/**
	 * Emits the conversion of the {@code Object} value on top of the stack into the given
	 * primitive {@code type}, accepting any smaller primitive wrapper via a widening
	 * conversion (mirroring the reflection semantics). For a {@code type} that admits no
	 * widening (boolean, byte, char) or a reference type, this behaves exactly like
	 * {@link #emitUnboxOrCast}. A value whose type does not match and cannot be widened
	 * results in a {@code HibernateAccessorException} being thrown.
	 */
	static void emitWideningUnbox(MethodVisitor mv, Class<?> type) {
		List<Class<?>> sources = WIDENING_SOURCES.get( type );
		if ( sources == null ) {
			// boolean, byte, char, or a reference type: nothing widens to it
			emitUnboxOrCast( mv, type );
			return;
		}

		BoxingInfo target = BOXING.get( type );
		Label end = new Label();

		// Fast path: the value already is the target's wrapper (the common case).
		Label notExact = new Label();
		mv.visitInsn( Opcodes.DUP );
		mv.visitTypeInsn( Opcodes.INSTANCEOF, target.wrapperInternal );
		mv.visitJumpInsn( Opcodes.IFEQ, notExact );
		mv.visitTypeInsn( Opcodes.CHECKCAST, target.wrapperInternal );
		mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, target.wrapperInternal, target.unboxMethod, target.unboxDesc, false );
		mv.visitJumpInsn( Opcodes.GOTO, end );
		mv.visitLabel( notExact );

		// Widening paths: unbox the smaller wrapper, then widen the primitive on the stack.
		for ( Class<?> source : sources ) {
			BoxingInfo si = BOXING.get( source );
			Label skip = new Label();
			mv.visitInsn( Opcodes.DUP );
			mv.visitTypeInsn( Opcodes.INSTANCEOF, si.wrapperInternal );
			mv.visitJumpInsn( Opcodes.IFEQ, skip );
			mv.visitTypeInsn( Opcodes.CHECKCAST, si.wrapperInternal );
			mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, si.wrapperInternal, si.unboxMethod, si.unboxDesc, false );
			emitPrimitiveWidening( mv, source, type );
			mv.visitJumpInsn( Opcodes.GOTO, end );
			mv.visitLabel( skip );
		}

		// No compatible type: discard the value and throw.
		mv.visitInsn( Opcodes.POP );
		mv.visitTypeInsn( Opcodes.NEW, EXCEPTION_INTERNAL );
		mv.visitInsn( Opcodes.DUP );
		mv.visitLdcInsn( "Cannot convert value to primitive type '" + type.getName() + "'" );
		mv.visitMethodInsn( Opcodes.INVOKESPECIAL, EXCEPTION_INTERNAL, "<init>", "(Ljava/lang/String;)V", false );
		mv.visitInsn( Opcodes.ATHROW );

		mv.visitLabel( end );
	}

	// Emits the widening primitive opcode (if any) to convert the source primitive on the
	// stack to the target primitive. byte/short/char/int all occupy the int stack category.
	private static void emitPrimitiveWidening(MethodVisitor mv, Class<?> source, Class<?> target) {
		if ( source == long.class ) {
			if ( target == float.class ) {
				mv.visitInsn( Opcodes.L2F );
			}
			else if ( target == double.class ) {
				mv.visitInsn( Opcodes.L2D );
			}
		}
		else if ( source == float.class ) {
			if ( target == double.class ) {
				mv.visitInsn( Opcodes.F2D );
			}
		}
		else {
			// byte/short/char/int -> int stack category
			if ( target == long.class ) {
				mv.visitInsn( Opcodes.I2L );
			}
			else if ( target == float.class ) {
				mv.visitInsn( Opcodes.I2F );
			}
			else if ( target == double.class ) {
				mv.visitInsn( Opcodes.I2D );
			}
			// target int/short: already the correct stack category, no conversion needed
		}
	}

	static void emitIntConstant(MethodVisitor mv, int value) {
		if ( value >= -1 && value <= 5 ) {
			mv.visitInsn( Opcodes.ICONST_0 + value );
		}
		else if ( value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE ) {
			mv.visitIntInsn( Opcodes.BIPUSH, value );
		}
		else if ( value >= Short.MIN_VALUE && value <= Short.MAX_VALUE ) {
			mv.visitIntInsn( Opcodes.SIPUSH, value );
		}
		else {
			mv.visitLdcInsn( value );
		}
	}


	private record BoxingInfo(String wrapperInternal, String primitiveDesc, String unboxMethod, String unboxDesc) {
		String valueOfDesc() {
			return "(" + primitiveDesc + ")L" + wrapperInternal + ";";
		}
	}
}
