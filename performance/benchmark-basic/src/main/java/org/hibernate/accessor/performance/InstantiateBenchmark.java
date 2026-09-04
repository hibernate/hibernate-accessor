/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;

import org.hibernate.accessor.HibernateAccessorFactory;
import org.hibernate.accessor.HibernateAccessorInstantiator;
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
 * Instantiates {@link BenchEntity} via {@link HibernateAccessorInstantiator#create} for a chosen
 * strategy, using either the no-arg or the all-args constructor.
 *
 * @see InstantiateBaseline for the hand-written raw and interface-dispatch reference points
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
public class InstantiateBenchmark {

	@Param({ "REFLECTION", "METHOD_HANDLE", "LAMBDA" })
	private Strategy strategy;

	@Param({ "false", "true" })
	private boolean withArgs;

	private HibernateAccessorInstantiator<BenchEntity> instantiator;
	private Object[] args;

	@Setup
	public void setUp() throws ReflectiveOperationException {
		HibernateAccessorFactory factory = strategy.create( MethodHandles.lookup() );
		if ( withArgs ) {
			this.instantiator = factory.instantiator(
					BenchEntity.class.getDeclaredConstructor( int.class, String.class ) );
			this.args = new Object[] { 42, "benchmark" };
		}
		else {
			this.instantiator = factory.instantiator( BenchEntity.class.getDeclaredConstructor() );
			this.args = new Object[0];
		}
	}

	@Benchmark
	public BenchEntity instantiate() {
		return instantiator.create( args );
	}
}
