/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.baseline;

import org.hibernate.accessor.HibernateAccessorInstantiator;
import org.hibernate.accessor.HibernateAccessorValueReader;
import org.hibernate.accessor.HibernateAccessorValueWriter;
import org.hibernate.accessor.performance.entities.BenchEntity;

/**
 * Hand-written implementations of the accessor interfaces for {@link BenchEntity}.
 *
 * <p>These are the "interface-dispatch" baseline: the benchmark loop is identical to the one used
 * for every strategy (same interface, same call shape), so the delta against a strategy isolates the
 * strategy's internal work (boxing, handle invocation, reflection) from the unavoidable cost of the
 * pluggable-interface indirection itself. The classes are {@code final} so that, when held at their
 * concrete type, the JIT can also devirtualize them for the raw/inlined baseline.
 *
 * <p>The bodies call the getters/setters, which the JIT inlines to plain field access -- the same
 * bytecode a generated strategy ultimately reaches.
 */
public final class DirectAccessors {

	private DirectAccessors() {
	}

	public static final class IntReader implements HibernateAccessorValueReader<Integer> {
		@Override
		public Integer get(Object instance) {
			return ( (BenchEntity) instance ).getIntValue();
		}
	}

	public static final class StringReader implements HibernateAccessorValueReader<String> {
		@Override
		public String get(Object instance) {
			return ( (BenchEntity) instance ).getStringValue();
		}
	}

	public static final class IntWriter implements HibernateAccessorValueWriter {
		@Override
		public void set(Object instance, Object value) {
			( (BenchEntity) instance ).setIntValue( (Integer) value );
		}
	}

	public static final class StringWriter implements HibernateAccessorValueWriter {
		@Override
		public void set(Object instance, Object value) {
			( (BenchEntity) instance ).setStringValue( (String) value );
		}
	}

	public static final class NoArgInstantiator implements HibernateAccessorInstantiator<BenchEntity> {
		@Override
		public BenchEntity create(Object... args) {
			return new BenchEntity();
		}
	}

	public static final class AllArgsInstantiator implements HibernateAccessorInstantiator<BenchEntity> {
		@Override
		public BenchEntity create(Object... args) {
			return new BenchEntity( (Integer) args[0], (String) args[1] );
		}
	}
}
