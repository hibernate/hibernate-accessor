/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.asm;

import org.hibernate.accessor.HibernateAccessorFactory;
import org.hibernate.accessor.HibernateAccessorMultiValueReader;
import org.hibernate.accessor.HibernateAccessorMultiValueWriter;
import org.hibernate.accessor.asm.spi.MultiValueAccessorPointcuts;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Member;

/**
 * Entry point for the ASM-based accessor strategy.
 *
 * <p>Creates a factory that generates one bulk accessor class per entity at runtime
 * using ASM bytecode generation with {@code TABLESWITCH} dispatch on field/method index.
 */
public interface HibernateAccessorAsmFactory extends HibernateAccessorFactory {

	/**
	 * Creates an ASM-based accessor factory using the given lookup for access control.
	 *
	 * @param lookup the lookup object that determines access rights
	 * @return a new ASM-based factory instance
	 */
	static HibernateAccessorAsmFactory factory(MethodHandles.Lookup lookup) {
		return new org.hibernate.accessor.asm.impl.HibernateAccessorAsmFactory( lookup);
	}

	HibernateAccessorMultiValueReader multiValueReader(
			Class<?> declaringClass,
			Member[] members,
			MultiValueAccessorPointcuts pointcuts);

	HibernateAccessorMultiValueWriter multiValueWriter(
			Class<?> declaringClass,
			Member[] members,
			MultiValueAccessorPointcuts pointcuts);
}
