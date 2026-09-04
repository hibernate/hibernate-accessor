/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;

import org.hibernate.accessor.HibernateAccessorFactory;
import org.hibernate.accessor.HibernateAccessorValueWriter;
import org.hibernate.accessor.performance.entities.BenchEntity;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Writes a single property via {@link HibernateAccessorValueWriter#set} for a chosen strategy,
 * across the field/method and primitive/reference axes.
 *
 * @see WriteBaseline for the hand-written raw and interface-dispatch reference points
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
public class WriteBenchmark {

	@Param({ "REFLECTION", "METHOD_HANDLE", "LAMBDA" })
	private Strategy strategy;

	@Param({ "FIELD", "METHOD" })
	private AccessKind access;

	@Param({ "PRIMITIVE", "REFERENCE" })
	private ValueKind valueKind;

	private HibernateAccessorValueWriter writer;
	private BenchEntity entity;
	private Object value;

	@Setup
	public void setUp() throws ReflectiveOperationException {
		HibernateAccessorFactory factory = strategy.create( MethodHandles.lookup() );
		this.entity = new BenchEntity();

		boolean primitive = valueKind == ValueKind.PRIMITIVE;
		this.value = primitive ? Integer.valueOf( 42 ) : "benchmark";
		if ( access == AccessKind.FIELD ) {
			this.writer = factory.valueWriter(
					BenchEntity.class.getDeclaredField( primitive ? "intValue" : "stringValue" ) );
		}
		else {
			this.writer = factory.valueWriter(
					BenchEntity.class.getMethod(
							primitive ? "setIntValue" : "setStringValue",
							primitive ? int.class : String.class ) );
		}
	}

	@Benchmark
	public void write() {
		// The write targets a field of the escaped @State entity, so it is an observable side effect.
		writer.set( entity, value );
	}
}
