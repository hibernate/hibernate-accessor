/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance;

import java.util.concurrent.TimeUnit;

import org.hibernate.accessor.HibernateAccessorValueWriter;
import org.hibernate.accessor.performance.baseline.DirectAccessors;
import org.hibernate.accessor.performance.entities.BenchEntity;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Hand-written reference points for {@link WriteBenchmark}. Not parameterized by strategy.
 *
 * <p>{@code raw*} writes the property directly (primitive stays unboxed); {@code iface*} writes it
 * through a hand-written {@link HibernateAccessorValueWriter}. See {@link ReadBaseline} for the
 * rationale of the two flavors and why field vs method is not an axis here.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
public class WriteBaseline {

	private BenchEntity entity;
	private final int intValue = 42;
	private final Integer boxedIntValue = 42;
	private final String stringValue = "benchmark";

	private final HibernateAccessorValueWriter intWriter = new DirectAccessors.IntWriter();
	private final HibernateAccessorValueWriter stringWriter = new DirectAccessors.StringWriter();

	@Setup
	public void setUp() {
		this.entity = new BenchEntity();
	}

	@Benchmark
	public void rawIntWrite() {
		entity.setIntValue( intValue );
	}

	@Benchmark
	public void rawStringWrite() {
		entity.setStringValue( stringValue );
	}

	@Benchmark
	public void ifaceIntWrite() {
		intWriter.set( entity, boxedIntValue );
	}

	@Benchmark
	public void ifaceStringWrite() {
		stringWriter.set( entity, stringValue );
	}
}
