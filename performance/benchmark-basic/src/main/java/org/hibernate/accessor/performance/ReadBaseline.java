/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance;

import java.util.concurrent.TimeUnit;

import org.hibernate.accessor.HibernateAccessorValueReader;
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
 * Hand-written reference points for {@link ReadBenchmark}. Not parameterized by strategy.
 *
 * <ul>
 *     <li>{@code raw*}: the property is read directly in the benchmark method (no interface), the
 *     absolute floor the JIT can fully inline -- the "is the abstraction worth it vs plain code" number.
 *     For the primitive case the value stays unboxed, so the boxing the strategies pay shows up as
 *     part of their overhead.</li>
 *     <li>{@code iface*}: the same read behind a hand-written {@link HibernateAccessorValueReader},
 *     so the call shape matches the strategies and the delta isolates strategy-internal work.</li>
 * </ul>
 *
 * <p>Field vs method is intentionally absent: a getter inlines to the same field load, so the
 * hand-written floor is identical for both.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
public class ReadBaseline {

	private BenchEntity entity;

	private final HibernateAccessorValueReader<Integer> intReader = new DirectAccessors.IntReader();
	private final HibernateAccessorValueReader<String> stringReader = new DirectAccessors.StringReader();

	@Setup
	public void setUp() {
		this.entity = new BenchEntity( 42, "benchmark" );
	}

	@Benchmark
	public int rawIntRead() {
		return entity.getIntValue();
	}

	@Benchmark
	public String rawStringRead() {
		return entity.getStringValue();
	}

	@Benchmark
	public Object ifaceIntRead() {
		return intReader.get( entity );
	}

	@Benchmark
	public Object ifaceStringRead() {
		return stringReader.get( entity );
	}
}
