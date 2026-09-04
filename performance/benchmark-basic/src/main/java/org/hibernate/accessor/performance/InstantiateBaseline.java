/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance;

import java.util.concurrent.TimeUnit;

import org.hibernate.accessor.HibernateAccessorInstantiator;
import org.hibernate.accessor.performance.baseline.DirectAccessors;
import org.hibernate.accessor.performance.entities.BenchEntity;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Hand-written reference points for {@link InstantiateBenchmark}. Not parameterized by strategy.
 *
 * <p>{@code raw*} calls {@code new BenchEntity(...)} directly; {@code iface*} goes through a
 * hand-written {@link HibernateAccessorInstantiator}. See {@link ReadBaseline} for the rationale.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
public class InstantiateBaseline {

	private final Object[] args = { 42, "benchmark" };

	private final HibernateAccessorInstantiator<BenchEntity> noArgInstantiator = new DirectAccessors.NoArgInstantiator();
	private final HibernateAccessorInstantiator<BenchEntity> allArgsInstantiator = new DirectAccessors.AllArgsInstantiator();

	@Benchmark
	public BenchEntity rawNoArg() {
		return new BenchEntity();
	}

	@Benchmark
	public BenchEntity rawAllArgs() {
		return new BenchEntity( 42, "benchmark" );
	}

	@Benchmark
	public BenchEntity ifaceNoArg() {
		return noArgInstantiator.create();
	}

	@Benchmark
	public BenchEntity ifaceAllArgs() {
		return allArgsInstantiator.create( args );
	}
}
