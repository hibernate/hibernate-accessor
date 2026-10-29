package org.hibernate.accessor.performance.aot;

import java.util.Arrays;

/** Experimental bytecode-size model for the benchmark's reference-only synthetic entities. */
final class ChunkSizing {
	private ChunkSizing() {
	}

	static int[] forReferenceModel(int types, int fields, int constructors, int fallback, int targetBytes) {
		if ( targetBytes <= 0 )
			throw new IllegalArgumentException( "targetMethodBytes must be positive" );
		int[] constructorCosts = new int[constructors];
		for ( int arity = 0; arity < constructors; arity++ )
			constructorCosts[arity] = 27 + 7 * arity;
		int maxArity = Math.max( 1, constructors - 1 );
		int constructorCases = pick( constructorCosts, 10, targetBytes, Math.max( 1, fallback / maxArity ) );
		// Preserve the exact fallback budget if the candidate is unchanged.
		int constructorBudget = constructorCases == Math.max( 1, fallback / maxArity )
				? fallback
				: Math.multiplyExact( constructorCases, maxArity );
		return new int[] {
				pick( costs( types, 13 ), 10, targetBytes, fallback ),
				pick( costs( types, 14 ), 11, targetBytes, fallback ),
				pick( costs( types, 13 ), 10, targetBytes, fallback ),
				pick( costs( fields, 12 ), 10, targetBytes, fallback ),
				pick( costs( fields, 16 ), 11, targetBytes, fallback ),
				constructorBudget
		};
	}

	private static int[] costs(int count, int cost) {
		int[] costs = new int[count];
		Arrays.fill( costs, cost );
		return costs;
	}

	/** Minimize the maximum method size; this does not predict throughput or guarantee inlining. */
	private static int pick(int[] costs, int dispatcherCaseBytes, int targetBytes, int fallback) {
		// Allow for switch headers, argument checks, alignment and failure paths.
		final int overhead = 64;
		long total = overhead;
		for ( int cost : costs )
			total += cost;
		if ( total <= targetBytes )
			return costs.length;
		int best = fallback;
		long bestBytes = Long.MAX_VALUE;
		// A single helper cannot fit more cases than this even at the cheapest case cost.
		int cheapest = Arrays.stream( costs ).min().orElseThrow();
		int upper = Math.min( costs.length, Math.max( 1, ( targetBytes - overhead ) / cheapest ) );
		for ( int size = 1; size <= upper; size++ ) {
			long largest = overhead + (long) dispatcherCaseBytes * ( ( costs.length - 1 ) / size + 1 );
			for ( int start = 0; start < costs.length; start += size ) {
				long bytes = overhead;
				for ( int i = start; i < Math.min( start + size, costs.length ); i++ )
					bytes += costs[i];
				largest = Math.max( largest, bytes );
			}
			// Prefer fewer helpers if the maximum sizes tie.
			if ( largest <= bestBytes ) {
				bestBytes = largest;
				best = size;
			}
		}
		return bestBytes <= targetBytes ? best : fallback;
	}
}
