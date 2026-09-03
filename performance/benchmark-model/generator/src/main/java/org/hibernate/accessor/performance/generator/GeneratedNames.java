/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.generator;

/**
 * Names shared between the entity host-method emitter and the shared-reader emitter so the two
 * halves of the double-switch agree on method/class names and member ordering.
 *
 * <p>Member ordering baked into every host {@code $$read*} switch and expected by the benchmark:
 * memberIndex {@code 0..fieldCount-1} are the scalar fields {@code f<i>}; memberIndex
 * {@code fieldCount..fieldCount+depth-1} are the reference fields {@code r<j>}.
 */
final class GeneratedNames {

	/** Host static method reading a member by index via direct field access ({@code GETFIELD}). */
	static final String READ_METHOD_FIELD = "$$read";
	/** Host static method reading a member by index via its getter ({@code INVOKEVIRTUAL}). */
	static final String READ_METHOD_GETTER = "$$readMethod";
	/** {@code (int memberIndex, Object instance) -> Object}. */
	static final String READ_METHOD_DESC = "(ILjava/lang/Object;)Ljava/lang/Object;";

	/** Simple name of the shared reader dispatching to {@link #READ_METHOD_FIELD}. */
	static final String READER_FIELD_SIMPLE = "GeneratedReaderField";
	/** Simple name of the shared reader dispatching to {@link #READ_METHOD_GETTER}. */
	static final String READER_METHOD_SIMPLE = "GeneratedReaderMethod";

	static final String READER_INTERFACE_INTERNAL = "org/hibernate/accessor/HibernateAccessorValueReader";

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Multi-value (bulk) host methods and shared accessor names
	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	/** Host static method reading all scalar fields into {@code Object[]} via direct field access. */
	static final String READ_ALL_METHOD_FIELD = "$$readAll";
	/** Host static method reading all scalar fields into {@code Object[]} via getters. */
	static final String READ_ALL_METHOD_GETTER = "$$readAllMethod";
	/** {@code (Object instance) -> Object[]}. */
	static final String READ_ALL_METHOD_DESC = "(Ljava/lang/Object;)[Ljava/lang/Object;";

	/** Host static method writing all scalar fields from {@code Object[]} via direct field access. */
	static final String WRITE_ALL_METHOD_FIELD = "$$writeAll";
	/** Host static method writing all scalar fields from {@code Object[]} via setters. */
	static final String WRITE_ALL_METHOD_GETTER = "$$writeAllMethod";
	/** {@code (Object instance, Object[] values) -> void}. */
	static final String WRITE_ALL_METHOD_DESC = "(Ljava/lang/Object;[Ljava/lang/Object;)V";

	/** Simple name of the shared multi-reader dispatching to {@link #READ_ALL_METHOD_FIELD}. */
	static final String MULTI_READER_FIELD_SIMPLE = "GeneratedMultiReaderField";
	/** Simple name of the shared multi-reader dispatching to {@link #READ_ALL_METHOD_GETTER}. */
	static final String MULTI_READER_METHOD_SIMPLE = "GeneratedMultiReaderMethod";
	/** Simple name of the shared multi-writer dispatching to {@link #WRITE_ALL_METHOD_FIELD}. */
	static final String MULTI_WRITER_FIELD_SIMPLE = "GeneratedMultiWriterField";
	/** Simple name of the shared multi-writer dispatching to {@link #WRITE_ALL_METHOD_GETTER}. */
	static final String MULTI_WRITER_METHOD_SIMPLE = "GeneratedMultiWriterMethod";

	static final String MULTI_READER_INTERFACE_INTERNAL = "org/hibernate/accessor/HibernateAccessorMultiValueReader";
	static final String MULTI_WRITER_INTERFACE_INTERNAL = "org/hibernate/accessor/HibernateAccessorMultiValueWriter";

	/**
	 * Above this many cases a switch is split into {@code <name>$0, <name>$1, ...} sub-methods behind a
	 * {@code switch(index / SWITCH_CHUNK_SIZE)} dispatcher, dodging the JIT huge-method limit -- matching
	 * {@code HibernateAccessorGenerationUtil.SWITCH_CHUNK_SIZE} in the Quarkus generator.
	 */
	static final int SWITCH_CHUNK_SIZE = 1000;

	private GeneratedNames() {
	}
}
