/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.util.concurrent.TimeUnit;

import org.hibernate.accessor.HibernateAccessorFactory;
import org.hibernate.accessor.HibernateAccessorValueReader;
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

/**
 * Walks a build-generated entity graph reading scalars via a chosen accessor strategy, so the
 * width (#fields) x count (#entities) x depth x read-hotness axes can be swept apples-to-apples
 * across strategies. {@link ReadMode} controls how many scalars per type are read (all vs a hot
 * subset), the axis that separates the whole-model double-switch from per-member code generation.
 *
 * <p>The model is generated once at build time (see {@code benchmark-model-generator}) and is
 * byte-identical across every strategy, fork and run. {@code walk()} folds all reads into a checksum;
 * {@link #setUp()} asserts that checksum against an independent reflective reference, so a divergent
 * strategy fails fast rather than silently mis-benchmarking.
 *
 * <p>Only the core strategies are listed as {@code @Param} defaults; ASM/ByteBuddy (and their
 * per-member variants) are selected via {@code -p strategy=...} when running the matching jar.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class GeneratedGraphBenchmark {

	@Param({
			"e8_f4_d2",
			"e8_f4_d4",
			"e8_f16_d2",
			"e8_f16_d4",
			"e8_f64_d2",
			"e8_f64_d4",
			"e64_f4_d2",
			"e64_f4_d4",
			"e64_f16_d2",
			"e64_f16_d4",
			"e64_f64_d2",
			"e64_f64_d4",
			"e256_f4_d2",
			"e256_f4_d4",
			"e256_f16_d2",
			"e256_f16_d4",
			"e256_f64_d2",
			"e256_f64_d4"
	})
	private String modelId;

	@Param({ "REFLECTION", "METHOD_HANDLE", "LAMBDA", "GENERATED_DOUBLE_SWITCH" })
	private Strategy strategy;

	@Param({ "FIELD", "METHOD" })
	private AccessKind access;

	@Param({ "ALL", "HOT_SUBSET" })
	private ReadMode readMode;

	private Object[] roots;
	private HibernateAccessorValueReader<?>[][] scalarReaders;
	private HibernateAccessorValueReader<?>[][] referenceReaders;
	private int[][] referenceLeafType;
	private int hotScalarCount;

	@Setup
	public void setUp() throws ReflectiveOperationException {
		GeneratedModel model = new GeneratedModel( modelId );

		int entityCount = model.entityCount();
		int fieldCount = model.fieldCount();
		int depth = model.depth();
		this.hotScalarCount = readMode.hotScalarCount( fieldCount );

		this.scalarReaders = new HibernateAccessorValueReader<?>[entityCount][fieldCount];
		this.referenceReaders = new HibernateAccessorValueReader<?>[entityCount][depth];
		this.referenceLeafType = new int[entityCount][depth];

		if ( strategy == Strategy.GENERATED_DOUBLE_SWITCH ) {
			buildDoubleSwitchReaders( model, entityCount, fieldCount, depth );
		}
		else {
			buildFactoryReaders( model, entityCount, fieldCount, depth );
		}

		this.roots = model.roots();

		long expected = model.expectedChecksum( hotScalarCount );
		long actual = walk();
		if ( actual != expected ) {
			throw new AssertionError(
					"Checksum mismatch for " + strategy + "/" + access + "/" + readMode + "/" + modelId
							+ ": expected " + expected + " but read " + actual );
		}
	}

	private void buildFactoryReaders(GeneratedModel model, int entityCount, int fieldCount, int depth) {
		HibernateAccessorFactory factory = strategy.create( MethodHandles.lookup() );
		boolean field = access == AccessKind.FIELD;

		for ( int t = 0; t < entityCount; t++ ) {
			for ( int i = 0; i < fieldCount; i++ ) {
				scalarReaders[t][i] = field
						? factory.valueReader( model.scalarField( t, i ) )
						: factory.valueReader( model.scalarGetter( t, i ) );
			}
			for ( int j = 0; j < depth; j++ ) {
				referenceReaders[t][j] = field
						? factory.valueReader( model.referenceField( t, j ) )
						: factory.valueReader( model.referenceGetter( t, j ) );
				referenceLeafType[t][j] = model.referenceLeafType( t, j );
			}
		}
	}

	// The double-switch is whole-model: every reader is an instance of the single shared reader class,
	// keyed by (classIndex, memberIndex). memberIndex 0..fieldCount-1 are scalars, then the references.
	private void buildDoubleSwitchReaders(GeneratedModel model, int entityCount, int fieldCount, int depth)
			throws ReflectiveOperationException {
		Constructor<?> ctor = model.switchReaderClass( access ).getDeclaredConstructor( int.class, int.class );

		for ( int t = 0; t < entityCount; t++ ) {
			for ( int i = 0; i < fieldCount; i++ ) {
				scalarReaders[t][i] = (HibernateAccessorValueReader<?>) ctor.newInstance( t, i );
			}
			for ( int j = 0; j < depth; j++ ) {
				referenceReaders[t][j] = (HibernateAccessorValueReader<?>) ctor.newInstance( t, fieldCount + j );
				referenceLeafType[t][j] = model.referenceLeafType( t, j );
			}
		}
	}

	@Benchmark
	public long walk() {
		long acc = 1L;
		Object[] r = this.roots;
		HibernateAccessorValueReader<?>[][] sr = this.scalarReaders;
		HibernateAccessorValueReader<?>[][] rr = this.referenceReaders;
		int[][] rl = this.referenceLeafType;
		int hot = this.hotScalarCount;

		for ( int t = 0; t < r.length; t++ ) {
			Object root = r[t];
			HibernateAccessorValueReader<?>[] rootScalars = sr[t];
			for ( int i = 0; i < hot; i++ ) {
				acc = acc * 31 + (Integer) rootScalars[i].get( root );
			}
			HibernateAccessorValueReader<?>[] refs = rr[t];
			int[] leafTypes = rl[t];
			for ( int j = 0; j < refs.length; j++ ) {
				Object leaf = refs[j].get( root );
				HibernateAccessorValueReader<?>[] leafScalars = sr[leafTypes[j]];
				for ( int i = 0; i < hot; i++ ) {
					acc = acc * 31 + (Integer) leafScalars[i].get( leaf );
				}
			}
		}
		return acc;
	}
}
