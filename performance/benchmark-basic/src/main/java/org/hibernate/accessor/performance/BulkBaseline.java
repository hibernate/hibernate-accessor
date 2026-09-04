/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance;

import java.util.concurrent.TimeUnit;

import org.hibernate.accessor.HibernateAccessorMultiValueReader;
import org.hibernate.accessor.HibernateAccessorMultiValueWriter;
import org.hibernate.accessor.performance.baseline.DirectWideAccessors;
import org.hibernate.accessor.performance.entities.WideEntity;

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
 * Hand-written reference points for {@link BulkBenchmark}. Not parameterized by strategy, but keeps
 * the member-count axis so it lines up with the strategy runs.
 *
 * <p>{@code raw*} calls the hand-written multi-value accessor at its concrete {@code final} type (so
 * the JIT can inline the whole body); {@code iface*} calls the same instance through the accessor
 * interface. The multi-value API is inherently {@code Object[]}, so both flavors box.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
public class BulkBaseline {

	@Param({ "1", "8", "32" })
	private int memberCount;

	private WideEntity entity;
	private Object[] values;

	private DirectWideAccessors.WideReader rawReader;
	private DirectWideAccessors.WideWriter rawWriter;
	private HibernateAccessorMultiValueReader ifaceReader;
	private HibernateAccessorMultiValueWriter ifaceWriter;

	@Setup
	public void setUp() {
		this.entity = new WideEntity();
		this.values = new Object[memberCount];
		for ( int i = 0; i < memberCount; i++ ) {
			this.values[i] = ( ( i % 2 ) == 0 ) ? Integer.valueOf( i ) : ( "v" + i );
		}
		this.rawReader = new DirectWideAccessors.WideReader( memberCount );
		this.rawWriter = new DirectWideAccessors.WideWriter( memberCount );
		this.ifaceReader = this.rawReader;
		this.ifaceWriter = this.rawWriter;
	}

	@Benchmark
	public Object[] rawBulkRead() {
		return rawReader.get( entity );
	}

	@Benchmark
	public void rawBulkWrite() {
		rawWriter.set( entity, values );
	}

	@Benchmark
	public Object[] ifaceBulkRead() {
		return ifaceReader.get( entity );
	}

	@Benchmark
	public void ifaceBulkWrite() {
		ifaceWriter.set( entity, values );
	}
}
