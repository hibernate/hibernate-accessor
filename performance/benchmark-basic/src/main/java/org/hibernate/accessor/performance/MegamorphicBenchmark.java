/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.hibernate.accessor.HibernateAccessorFactory;
import org.hibernate.accessor.HibernateAccessorValueReader;
import org.hibernate.accessor.HibernateAccessorValueWriter;
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
 * Reads one property from each of many distinct entity types at a single call site.
 *
 * <p>The realistic multi-entity case: with {@code polymorphic=true} the call site sees a different
 * concrete accessor class per element (megamorphic, defeating inlining); with
 * {@code polymorphic=false} the same accessor class and receiver type are used throughout
 * (monomorphic baseline). Each invocation performs one read (or write) per
 * {@link MegaEntities#CLASSES entry}.
 *
 * @see MegamorphicBaseline for the hand-written reference points
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
public class MegamorphicBenchmark {

	@Param({ "REFLECTION", "METHOD_HANDLE", "LAMBDA" })
	private Strategy strategy;

	@Param({ "false", "true" })
	private boolean polymorphic;

	private HibernateAccessorValueReader<?>[] readers;
	private HibernateAccessorValueWriter[] writers;
	private Object[] instances;
	private Object[] values;

	@Setup
	public void setUp() throws ReflectiveOperationException {
		HibernateAccessorFactory factory = strategy.create( MethodHandles.lookup() );
		List<Class<?>> classes = MegaEntities.CLASSES;
		int n = classes.size();
		this.readers = new HibernateAccessorValueReader<?>[n];
		this.writers = new HibernateAccessorValueWriter[n];
		this.instances = new Object[n];
		this.values = new Object[n];
		for ( int i = 0; i < n; i++ ) {
			Class<?> type = polymorphic ? classes.get( i ) : classes.get( 0 );
			this.instances[i] = type.getDeclaredConstructor( int.class ).newInstance( i );
			this.values[i] = Integer.valueOf( i );
			this.readers[i] = factory.valueReader( type.getMethod( "getValue" ) );
			this.writers[i] = factory.valueWriter( type.getMethod( "setValue", int.class ) );
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
