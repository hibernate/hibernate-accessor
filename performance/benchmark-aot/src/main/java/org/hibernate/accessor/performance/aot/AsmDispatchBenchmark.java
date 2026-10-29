package org.hibernate.accessor.performance.aot;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.hibernate.accessor.asm.aot.AotChunkSizes;
import org.hibernate.accessor.testing.aot.asm.AsmDispatchModel;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/** All generation, lookup and dispatch validation happens before measurement. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class AsmDispatchBenchmark {
	@Param({ "balanced" })
	public String model;
	// Zero selects the model preset. Counts stay fixed across chunk sweeps.
	@Param({ "0" })
	public int entities;
	@Param({ "0" })
	public int fields;
	@Param({ "0" })
	public int constructors;
	@Param({ "spread" })
	public String accessPattern;
	@Param({ "256" })
	public int chunkSize;
	@Param({ "fixed" })
	public String chunkPolicy;
	@Param({ "325" })
	public int targetMethodBytes;
	// Zero inherits chunkSize; override any operation/level independently.
	@Param({ "0" })
	public int typeRead;
	@Param({ "0" })
	public int typeWrite;
	@Param({ "0" })
	public int typeConstructor;
	@Param({ "0" })
	public int memberRead;
	@Param({ "0" })
	public int memberWrite;
	@Param({ "0" })
	public int memberConstructor;
	@Param({ "500" })
	public int typeLookup;
	@Param({ "500" })
	public int memberLookup;

	private AsmDispatchModel workload;
	private int fieldPosition;
	private int constructorPosition;
	private int[] fieldSamples;
	private int[] constructorSamples;
	private final Object writeValue = new Object();

	@Setup(Level.Trial)
	public void setup() throws Exception {
		int[] preset = switch ( model ) {
			case "types" -> new int[] { 1024, 4, 1 };
			case "members" -> new int[] { 4, 512, 1 };
			case "balanced" -> new int[] { 192, 192, 1 };
			case "large" -> new int[] { 512, 512, 1 };
			case "constructors" -> new int[] { 64, 4, 16 };
			default -> throw new IllegalArgumentException( "Unknown model: " + model );
		};
		if ( !Set.of( "hot", "sampled", "spread" ).contains( accessPattern ) ) {
			throw new IllegalArgumentException( "Unknown access pattern: " + accessPattern );
		}
		int typeCount = count( entities, preset[0] );
		int fieldCount = count( fields, preset[1] );
		int constructorCount = count( constructors, preset[2] );
		if ( chunkSize <= 0 )
			throw new IllegalArgumentException( "chunkSize must be positive" );
		int[] defaults = switch ( chunkPolicy ) {
			case "fixed" -> new int[] { chunkSize, chunkSize, chunkSize, chunkSize, chunkSize, chunkSize };
			case "auto" -> ChunkSizing.forReferenceModel( typeCount, fieldCount, constructorCount, chunkSize, targetMethodBytes );
			default -> throw new IllegalArgumentException( "Unknown chunk policy: " + chunkPolicy );
		};
		var chunks = new AotChunkSizes( count( typeRead, defaults[0] ), count( typeWrite, defaults[1] ), count( typeConstructor, defaults[2] ),
				count( memberRead, defaults[3] ), count( memberWrite, defaults[4] ), count( memberConstructor, defaults[5] ), typeLookup, memberLookup );
		workload = new AsmDispatchModel( typeCount, fieldCount, constructorCount, chunks, false );
		fieldSamples = samples( typeCount, fieldCount );
		constructorSamples = samples( typeCount, constructorCount );
		BytecodeReport.write( getClass().getSimpleName(), model, accessPattern, typeCount, fieldCount,
				constructorCount, chunks.toString(), workload.bytecode );
	}

	@Benchmark
	public Object read() {
		int index = next( fieldPosition++, workload.readers.length, fieldSamples );
		return workload.readers[index].get( workload.targets[index] );
	}

	@Benchmark
	public Object write() {
		int index = next( fieldPosition++, workload.writers.length, fieldSamples );
		workload.writers[index].set( workload.targets[index], writeValue );
		return workload.targets[index];
	}

	@Benchmark
	public Object construct() {
		int index = next( constructorPosition++, workload.constructors.length, constructorSamples );
		return workload.constructors[index].create( workload.arguments[index] );
	}

	private int next(int position, int count, int[] samples) {
		return switch ( accessPattern ) {
			case "hot" -> ( position & Integer.MAX_VALUE ) % Math.min( 4, count );
			case "sampled" -> samples[position & 15];
			default -> ( position & Integer.MAX_VALUE ) % count;
		};
	}

	private static int[] samples(int types, int members) {
		int[] result = new int[16];
		for ( int t = 0; t < 4; t++ ) {
			for ( int m = 0; m < 4; m++ )
				result[t * 4 + m] = ( t * ( types - 1 ) / 3 ) * members + m * ( members - 1 ) / 3;
		}
		return result;
	}


	private static int count(int override, int fallback) {
		if ( override < 0 || fallback <= 0 )
			throw new IllegalArgumentException( "Counts/sizes must be positive (zero inherits)" );
		return override == 0 ? fallback : override;
	}
}
