/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;

import org.hibernate.accessor.HibernateAccessorFactory;
import org.hibernate.accessor.performance.model.GeneratedModel;

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
 * The cost side of the ledger: how long it takes to turn a model's members into working readers --
 * i.e. the first-use / startup latency a strategy imposes, dominated by code generation for ASM and
 * ByteBuddy and by handle bootstrap for the method-handle/lambda strategies.
 *
 * <p>Runs in {@link Mode#SingleShotTime} with <em>zero warmup</em> and a fresh JVM per fork, so each
 * measured invocation hits a cold codegen cache and reports the true build-from-scratch cost rather
 * than a cache lookup. Only the throughput of the built readers is covered by
 * {@link GeneratedGraphBenchmark}; this deliberately builds and discards them.
 *
 * <p>Only the core strategies are {@code @Param} defaults; ASM/ByteBuddy are selected via
 * {@code -p strategy=...} on the matching jar, as in {@link GeneratedGraphBenchmark}.
 * {@code GENERATED_DOUBLE_SWITCH} is excluded -- it has no runtime factory (it is a build-time
 * strategy), so it is not a candidate for the runtime-only default this benchmark informs.
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 0)
@Measurement(iterations = 1)
@Fork(10)
public class AccessorBuildBenchmark {

	@Param({
			"e8_f16_d2",
			"e64_f64_d2",
			"e256_f16_d2"
	})
	private String modelId;

	@Param({ "REFLECTION", "METHOD_HANDLE", "LAMBDA" })
	private Strategy strategy;

	@Param({ "FIELD", "METHOD" })
	private AccessKind access;

	private GeneratedModel model;
	private int entityCount;
	private int fieldCount;
	private int depth;

	@Setup
	public void setUp() {
		this.model = new GeneratedModel( modelId );
		this.entityCount = model.entityCount();
		this.fieldCount = model.fieldCount();
		this.depth = model.depth();
	}

	/**
	 * Builds a reader for every member of every type with a freshly created factory, sinking each into
	 * the blackhole. The factory creation is inside the measured method on purpose: it is part of the
	 * startup cost a strategy imposes.
	 */
	@Benchmark
	public void buildAllReaders(Blackhole blackhole) {
		HibernateAccessorFactory factory = strategy.create( MethodHandles.lookup() );
		boolean field = access == AccessKind.FIELD;

		for ( int t = 0; t < entityCount; t++ ) {
			for ( int i = 0; i < fieldCount; i++ ) {
				blackhole.consume( field
						? factory.valueReader( model.scalarField( t, i ) )
						: factory.valueReader( model.scalarGetter( t, i ) ) );
			}
			for ( int j = 0; j < depth; j++ ) {
				blackhole.consume( field
						? factory.valueReader( model.referenceField( t, j ) )
						: factory.valueReader( model.referenceGetter( t, j ) ) );
			}
		}
	}
}
