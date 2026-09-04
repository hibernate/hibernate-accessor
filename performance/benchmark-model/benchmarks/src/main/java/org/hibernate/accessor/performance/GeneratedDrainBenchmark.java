/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.util.concurrent.TimeUnit;

import org.hibernate.accessor.HibernateAccessorFactory;
import org.hibernate.accessor.HibernateAccessorInstantiator;
import org.hibernate.accessor.HibernateAccessorMultiValueReader;
import org.hibernate.accessor.HibernateAccessorMultiValueWriter;
import org.hibernate.accessor.HibernateAccessorValueReader;
import org.hibernate.accessor.HibernateAccessorValueWriter;
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
 * Simulates the Hibernate ORM result-set drain pattern: reading all scalar values from entities
 * (dehydration / dirty-checking) and writing all scalar values into freshly instantiated entities
 * (hydration from a {@code ResultSet}). The key comparison axis is {@link DrainMode}: looping over
 * individual single-value accessors vs a single multi-value accessor call per entity.
 *
 * <p>Unlike {@link GeneratedGraphBenchmark} which walks a reference graph property-by-property
 * (Hibernate Validator pattern), this benchmark operates on flat scalar state only — every scalar
 * field of every entity type is read or written in each iteration, with no reference traversal.
 * Depth is irrelevant, so only {@code d2} models are listed as defaults.
 *
 * <p>The {@code GENERATED_DOUBLE_SWITCH} strategy is not listed in the default {@code @Param} grid
 * because it only supports {@link DrainMode#MULTI_ACCESSOR} (no generated single-value writers
 * exist). Select it explicitly with {@code -p strategy=GENERATED_DOUBLE_SWITCH -p drainMode=MULTI_ACCESSOR}
 * to measure the monomorphic build-time bulk path against the factory-based strategies.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class GeneratedDrainBenchmark {

	@Param({
			"e8_f4_d2",
			"e8_f16_d2",
			"e8_f64_d2",
			"e64_f4_d2",
			"e64_f16_d2",
			"e64_f64_d2",
			"e256_f4_d2",
			"e256_f16_d2",
			"e256_f64_d2"
	})
	private String modelId;

	@Param({ "REFLECTION", "METHOD_HANDLE", "LAMBDA" })
	private Strategy strategy;

	@Param({ "FIELD", "METHOD" })
	private AccessKind access;

	@Param({ "SINGLE_ACCESSOR", "MULTI_ACCESSOR" })
	private DrainMode drainMode;

	private int entityCount;
	private int fieldCount;

	// Pre-populated entity instances for drainRead
	private Object[] entities;
	// Pre-computed value arrays per entity type for drainWrite
	private Object[][] rowValues;

	// Instantiators (shared by both modes)
	private HibernateAccessorInstantiator<?>[] instantiators;

	// SINGLE_ACCESSOR mode
	private HibernateAccessorValueReader<?>[][] singleReaders;
	private HibernateAccessorValueWriter[][] singleWriters;

	// MULTI_ACCESSOR mode
	private HibernateAccessorMultiValueReader[] multiReaders;
	private HibernateAccessorMultiValueWriter[] multiWriters;

	@Setup
	public void setUp() throws ReflectiveOperationException {
		GeneratedModel model = new GeneratedModel( modelId );

		this.entityCount = model.entityCount();
		this.fieldCount = model.fieldCount();
		this.entities = model.roots();

		buildRowValues( model );

		if ( strategy == Strategy.GENERATED_DOUBLE_SWITCH ) {
			if ( drainMode == DrainMode.SINGLE_ACCESSOR ) {
				throw new UnsupportedOperationException(
						"GENERATED_DOUBLE_SWITCH only supports MULTI_ACCESSOR in the drain benchmark "
								+ "(no generated single-value writers exist)." );
			}
			buildDoubleSwitchAccessors( model );
		}
		else {
			HibernateAccessorFactory factory = strategy.create( MethodHandles.lookup() );
			buildInstantiators( model, factory );
			if ( drainMode == DrainMode.SINGLE_ACCESSOR ) {
				buildSingleAccessors( model, factory );
			}
			else {
				buildMultiAccessors( model, factory );
			}
		}

		long expected = computeExpectedDrainChecksum( model );
		long actual = drainRead();
		if ( actual != expected ) {
			throw new AssertionError(
					"Read-checksum mismatch for " + strategy + "/" + access + "/" + drainMode + "/" + modelId
							+ ": expected " + expected + " but got " + actual );
		}

		Object[] written = drainWrite();
		long writeChecksum = drainReadEntities( written );
		if ( writeChecksum != expected ) {
			throw new AssertionError(
					"Write-checksum mismatch for " + strategy + "/" + access + "/" + drainMode + "/" + modelId
							+ ": expected " + expected + " but got " + writeChecksum );
		}
	}

	private void buildDoubleSwitchAccessors(GeneratedModel model) throws ReflectiveOperationException {
		HibernateAccessorFactory reflectionFactory = HibernateAccessorFactory.reflection();
		this.instantiators = new HibernateAccessorInstantiator<?>[entityCount];
		for ( int t = 0; t < entityCount; t++ ) {
			instantiators[t] = reflectionFactory.instantiator( model.entityConstructor( t ) );
		}

		Constructor<?> readerCtor = model.switchMultiReaderClass( access ).getDeclaredConstructor( int.class );
		Constructor<?> writerCtor = model.switchMultiWriterClass( access ).getDeclaredConstructor( int.class );
		this.multiReaders = new HibernateAccessorMultiValueReader[entityCount];
		this.multiWriters = new HibernateAccessorMultiValueWriter[entityCount];
		for ( int t = 0; t < entityCount; t++ ) {
			multiReaders[t] = (HibernateAccessorMultiValueReader) readerCtor.newInstance( t );
			multiWriters[t] = (HibernateAccessorMultiValueWriter) writerCtor.newInstance( t );
		}
	}

	private void buildInstantiators(GeneratedModel model, HibernateAccessorFactory factory) {
		this.instantiators = new HibernateAccessorInstantiator<?>[entityCount];
		for ( int t = 0; t < entityCount; t++ ) {
			instantiators[t] = factory.instantiator( model.entityConstructor( t ) );
		}
	}

	private void buildRowValues(GeneratedModel model) throws ReflectiveOperationException {
		this.rowValues = new Object[entityCount][fieldCount];
		for ( int t = 0; t < entityCount; t++ ) {
			for ( int i = 0; i < fieldCount; i++ ) {
				rowValues[t][i] = model.scalarField( t, i ).getInt( entities[t] );
			}
		}
	}

	private void buildSingleAccessors(GeneratedModel model, HibernateAccessorFactory factory) {
		boolean field = access == AccessKind.FIELD;
		this.singleReaders = new HibernateAccessorValueReader<?>[entityCount][fieldCount];
		this.singleWriters = new HibernateAccessorValueWriter[entityCount][fieldCount];

		for ( int t = 0; t < entityCount; t++ ) {
			for ( int i = 0; i < fieldCount; i++ ) {
				if ( field ) {
					singleReaders[t][i] = factory.valueReader( model.scalarField( t, i ) );
					singleWriters[t][i] = factory.valueWriter( model.scalarField( t, i ) );
				}
				else {
					singleReaders[t][i] = factory.valueReader( model.scalarGetter( t, i ) );
					singleWriters[t][i] = factory.valueWriter( model.scalarSetter( t, i ) );
				}
			}
		}
	}

	private void buildMultiAccessors(GeneratedModel model, HibernateAccessorFactory factory) {
		boolean field = access == AccessKind.FIELD;
		this.multiReaders = new HibernateAccessorMultiValueReader[entityCount];
		this.multiWriters = new HibernateAccessorMultiValueWriter[entityCount];

		for ( int t = 0; t < entityCount; t++ ) {
			Member[] readMembers = new Member[fieldCount];
			Member[] writeMembers = new Member[fieldCount];
			for ( int i = 0; i < fieldCount; i++ ) {
				if ( field ) {
					readMembers[i] = model.scalarField( t, i );
					writeMembers[i] = readMembers[i];
				}
				else {
					readMembers[i] = model.scalarGetter( t, i );
					writeMembers[i] = model.scalarSetter( t, i );
				}
			}
			multiReaders[t] = factory.multiValueReader( model.entityType( t ), readMembers );
			multiWriters[t] = factory.multiValueWriter( model.entityType( t ), writeMembers );
		}
	}

	private long computeExpectedDrainChecksum(GeneratedModel model) throws ReflectiveOperationException {
		long acc = 1L;
		for ( int t = 0; t < entityCount; t++ ) {
			Object entity = entities[t];
			for ( int i = 0; i < fieldCount; i++ ) {
				acc = acc * 31 + model.scalarField( t, i ).getInt( entity );
			}
		}
		return acc;
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Read benchmark: extract all scalar values from pre-populated entities
	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	@Benchmark
	public long drainRead() {
		if ( drainMode == DrainMode.SINGLE_ACCESSOR ) {
			return drainReadSingle();
		}
		else {
			return drainReadMulti();
		}
	}

	private long drainReadSingle() {
		long acc = 1L;
		Object[] ents = this.entities;
		HibernateAccessorValueReader<?>[][] sr = this.singleReaders;
		int fc = this.fieldCount;

		for ( int t = 0; t < ents.length; t++ ) {
			Object entity = ents[t];
			HibernateAccessorValueReader<?>[] readers = sr[t];
			for ( int i = 0; i < fc; i++ ) {
				acc = acc * 31 + (Integer) readers[i].get( entity );
			}
		}
		return acc;
	}

	private long drainReadMulti() {
		long acc = 1L;
		Object[] ents = this.entities;
		HibernateAccessorMultiValueReader[] mr = this.multiReaders;

		for ( int t = 0; t < ents.length; t++ ) {
			Object[] values = mr[t].get( ents[t] );
			for ( int i = 0; i < values.length; i++ ) {
				acc = acc * 31 + (Integer) values[i];
			}
		}
		return acc;
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Write benchmark: instantiate entities and populate from value arrays
	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	@Benchmark
	public Object[] drainWrite() {
		if ( drainMode == DrainMode.SINGLE_ACCESSOR ) {
			return drainWriteSingle();
		}
		else {
			return drainWriteMulti();
		}
	}

	private Object[] drainWriteSingle() {
		Object[] result = new Object[entityCount];
		HibernateAccessorInstantiator<?>[] inst = this.instantiators;
		HibernateAccessorValueWriter[][] sw = this.singleWriters;
		Object[][] rv = this.rowValues;
		int fc = this.fieldCount;

		for ( int t = 0; t < entityCount; t++ ) {
			Object entity = inst[t].create();
			HibernateAccessorValueWriter[] writers = sw[t];
			Object[] vals = rv[t];
			for ( int i = 0; i < fc; i++ ) {
				writers[i].set( entity, vals[i] );
			}
			result[t] = entity;
		}
		return result;
	}

	private Object[] drainWriteMulti() {
		Object[] result = new Object[entityCount];
		HibernateAccessorInstantiator<?>[] inst = this.instantiators;
		HibernateAccessorMultiValueWriter[] mw = this.multiWriters;
		Object[][] rv = this.rowValues;

		for ( int t = 0; t < entityCount; t++ ) {
			Object entity = inst[t].create();
			mw[t].set( entity, rv[t] );
			result[t] = entity;
		}
		return result;
	}

	// Used by setUp to verify drainWrite correctness
	private long drainReadEntities(Object[] ents) {
		if ( drainMode == DrainMode.SINGLE_ACCESSOR ) {
			long acc = 1L;
			HibernateAccessorValueReader<?>[][] sr = this.singleReaders;
			int fc = this.fieldCount;
			for ( int t = 0; t < ents.length; t++ ) {
				Object entity = ents[t];
				HibernateAccessorValueReader<?>[] readers = sr[t];
				for ( int i = 0; i < fc; i++ ) {
					acc = acc * 31 + (Integer) readers[i].get( entity );
				}
			}
			return acc;
		}
		else {
			long acc = 1L;
			HibernateAccessorMultiValueReader[] mr = this.multiReaders;
			for ( int t = 0; t < ents.length; t++ ) {
				Object[] values = mr[t].get( ents[t] );
				for ( int i = 0; i < values.length; i++ ) {
					acc = acc * 31 + (Integer) values[i];
				}
			}
			return acc;
		}
	}
}
