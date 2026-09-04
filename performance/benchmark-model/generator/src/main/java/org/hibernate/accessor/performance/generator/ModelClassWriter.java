/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.generator;

import org.objectweb.asm.ClassWriter;

/**
 * A {@link ClassWriter} safe to use with {@link ClassWriter#COMPUTE_FRAMES} while emitting classes
 * that reference sibling generated types not yet loadable.
 *
 * <p>The generated switch methods never merge two reference types at a control-flow join (each case
 * returns), so {@link #getCommonSuperClass} is never actually consulted for a meaningful merge.
 * Overriding it to {@code java/lang/Object} without loading anything guarantees frame computation
 * cannot trigger a {@link ClassNotFoundException} on a type that is still being generated.
 */
final class ModelClassWriter extends ClassWriter {

	ModelClassWriter() {
		super( COMPUTE_FRAMES );
	}

	@Override
	protected String getCommonSuperClass(String type1, String type2) {
		return "java/lang/Object";
	}
}
