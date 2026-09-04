/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.generator;

/**
 * One row of the benchmark manifest: the knobs that fully determine a generated model.
 *
 * @param modelId     stable identifier; also the last package segment of the generated entities
 * @param entityCount number of distinct entity types (one root instance each)
 * @param fieldCount  scalar {@code int} members per type
 * @param depth       to-one reference members per type ({@code E_i.r_j -> E_((i+1+j) % entityCount)})
 * @param seed        seed for the deterministic scalar values
 */
public record ModelSpec(String modelId, int entityCount, int fieldCount, int depth, int seed) {

	public ModelSpec {
		if ( depth >= entityCount ) {
			throw new IllegalArgumentException(
					"Model '" + modelId + "': depth (" + depth + ") must be < entityCount (" + entityCount
							+ ") so every reference points at a distinct sibling type." );
		}
	}

	/** The generated entities' package, e.g. {@code org.hibernate.accessor.performance.generated.e8_f4_d2}. */
	public String packageName() {
		return "org.hibernate.accessor.performance.generated." + modelId;
	}
}
