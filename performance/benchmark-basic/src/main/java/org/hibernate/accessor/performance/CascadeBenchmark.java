/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;

import org.hibernate.accessor.HibernateAccessorFactory;
import org.hibernate.accessor.performance.entities.book.BookGraph;
import org.hibernate.accessor.performance.entities.book.Order;

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
 * A realistic cascading traversal: walk a whole book-order graph (order, customer, address, lines,
 * books, authors, publishers), reading every property through a strategy's accessors.
 *
 * <p>Unlike the single-property microbenchmarks, this exercises the accessors the way a validator or an
 * ORM does -- many distinct entity types funnelled through the two shared, megamorphic call sites in
 * {@link CascadeWalker}. That is the case where a code-generating strategy (ASM/ByteBuddy) is expected
 * to pull ahead of reflection/method-handle, and where the field-vs-getter split (see
 * {@link CascadeAccess}) shows whether a mixed factory -- lambdas for getters, generated code for
 * fields -- would pay off end to end.
 *
 * @see CascadeBaseline for the hand-written raw and interface-dispatch reference points
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class CascadeBenchmark {

	@Param({ "REFLECTION", "METHOD_HANDLE", "LAMBDA" })
	private Strategy strategy;

	@Param({ "FIELD", "METHOD", "MIXED" })
	private CascadeAccess access;

	@Param("8")
	private int lineCount;

	private CascadeWalker walker;
	private Order order;

	@Setup
	public void setUp() throws ReflectiveOperationException {
		HibernateAccessorFactory factory = strategy.create( MethodHandles.lookup() );
		this.walker = CascadeWalker.forStrategy( factory, access );
		this.order = BookGraph.sampleOrder( lineCount );
	}

	@Benchmark
	public long cascade() {
		return walker.walk( order );
	}
}
