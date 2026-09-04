/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.generator;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Small bytecode helpers shared by the emitters, ported verbatim from
 * {@code HibernateAccessorGenerationUtil} in the Quarkus generator.
 */
final class AsmSupport {

	private AsmSupport() {
	}

	/** Pushes an {@code int} constant using the most compact opcode, matching the Quarkus helper. */
	static void pushIntConst(MethodVisitor mv, int value) {
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
}
