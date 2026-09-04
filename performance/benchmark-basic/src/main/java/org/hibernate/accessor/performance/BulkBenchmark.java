/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Member;
import java.util.concurrent.TimeUnit;

import org.hibernate.accessor.HibernateAccessorFactory;
import org.hibernate.accessor.HibernateAccessorMultiValueReader;
import org.hibernate.accessor.HibernateAccessorMultiValueWriter;
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
 * Reads and writes several members at once via the multi-value accessors, across strategy,
 * field/method access, and member-count (arity) axes. This is where the batched path should pull
 * ahead of looping single accessors.
 *
 * @see BulkBaseline for the hand-written raw and interface-dispatch reference points
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
public class BulkBenchmark {

	@Param({ "REFLECTION", "METHOD_HANDLE", "LAMBDA" })
	private Strategy strategy;

	@Param({ "FIELD", "METHOD" })
	private AccessKind access;

	@Param({ "1", "8", "32" })
	private int memberCount;

	private HibernateAccessorMultiValueReader reader;
	private HibernateAccessorMultiValueWriter writer;
	private WideEntity entity;
	private Object[] values;

	@Setup
	public void setUp() throws ReflectiveOperationException {
		HibernateAccessorFactory factory = strategy.create( MethodHandles.lookup() );
		this.entity = new WideEntity();

		Member[] readMembers = new Member[memberCount];
		Member[] writeMembers = new Member[memberCount];
		this.values = new Object[memberCount];
		for ( int i = 0; i < memberCount; i++ ) {
			boolean primitive = ( i % 2 ) == 0;
			this.values[i] = primitive ? Integer.valueOf( i ) : ( "v" + i );
			if ( access == AccessKind.FIELD ) {
				readMembers[i] = WideEntity.class.getDeclaredField( "field" + i );
				writeMembers[i] = readMembers[i];
			}
			else {
				readMembers[i] = WideEntity.class.getMethod( "getField" + i );
				writeMembers[i] = WideEntity.class.getMethod(
						"setField" + i, primitive ? int.class : String.class );
			}
		}

		this.reader = factory.multiValueReader( WideEntity.class, readMembers );
		this.writer = factory.multiValueWriter( WideEntity.class, writeMembers );
	}

	@Benchmark
	public Object[] bulkRead() {
		return reader.get( entity );
	}

	@Benchmark
	public void bulkWrite() {
		writer.set( entity, values );
	}
}
