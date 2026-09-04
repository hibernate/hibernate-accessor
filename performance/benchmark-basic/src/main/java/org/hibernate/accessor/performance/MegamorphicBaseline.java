/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.hibernate.accessor.HibernateAccessorValueReader;
import org.hibernate.accessor.HibernateAccessorValueWriter;
import org.hibernate.accessor.performance.baseline.DirectMegaReaders;
import org.hibernate.accessor.performance.baseline.DirectMegaWriters;
import org.hibernate.accessor.performance.entities.mega.MegaEntities;

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
import org.openjdk.jmh.infra.Blackhole;

/**
 * Hand-written reference point for {@link MegamorphicBenchmark}: the same call-site-shape experiment
 * using hand-written value readers, so the megamorphic penalty can be attributed to call-site shape
 * rather than to any strategy's internals.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
public class MegamorphicBaseline {

	@Param({ "false", "true" })
	private boolean polymorphic;

	private HibernateAccessorValueReader<?>[] readers;
	private HibernateAccessorValueWriter[] writers;
	private Object[] instances;
	private Object[] values;

	@Setup
	public void setUp() throws ReflectiveOperationException {
		List<Class<?>> classes = MegaEntities.CLASSES;
		int n = classes.size();
		HibernateAccessorValueReader<?>[] handWrittenReaders = DirectMegaReaders.readers();
		HibernateAccessorValueWriter[] handWrittenWriters = DirectMegaWriters.writers();
		this.readers = new HibernateAccessorValueReader<?>[n];
		this.writers = new HibernateAccessorValueWriter[n];
		this.instances = new Object[n];
		this.values = new Object[n];
		for ( int i = 0; i < n; i++ ) {
			Class<?> type = polymorphic ? classes.get( i ) : classes.get( 0 );
			this.instances[i] = type.getDeclaredConstructor( int.class ).newInstance( i );
			this.values[i] = Integer.valueOf( i );
			this.readers[i] = polymorphic ? handWrittenReaders[i] : handWrittenReaders[0];
			this.writers[i] = polymorphic ? handWrittenWriters[i] : handWrittenWriters[0];
		}
	}

	@Benchmark
	public void megamorphicRead(Blackhole bh) {
		HibernateAccessorValueReader<?>[] rs = this.readers;
		Object[] is = this.instances;
		for ( int i = 0; i < rs.length; i++ ) {
			bh.consume( rs[i].get( is[i] ) );
		}
	}

	@Benchmark
	public void megamorphicWrite() {
		HibernateAccessorValueWriter[] ws = this.writers;
		Object[] is = this.instances;
		Object[] vs = this.values;
		for ( int i = 0; i < ws.length; i++ ) {
			// Each write targets a field of an escaped @State instance, so it is an observable side effect.
			ws[i].set( is[i], vs[i] );
		}
	}
}
