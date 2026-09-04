/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the checked-in benchmark manifest into {@link ModelSpec}s.
 *
 * <p>Each non-blank, non-{@code #} line is {@code modelId, entityCount, fieldCount, depth [, seed]}.
 */
public final class ManifestParser {

	private static final int DEFAULT_SEED = 42;

	private ManifestParser() {
	}

	public static List<ModelSpec> parse(Path manifest) throws IOException {
		List<ModelSpec> specs = new ArrayList<>();
		List<String> lines = Files.readAllLines( manifest );
		for ( int lineNo = 1; lineNo <= lines.size(); lineNo++ ) {
			String raw = lines.get( lineNo - 1 ).trim();
			if ( raw.isEmpty() || raw.startsWith( "#" ) ) {
				continue;
			}
			String[] tokens = raw.split( "," );
			if ( tokens.length < 4 || tokens.length > 5 ) {
				throw new IllegalArgumentException(
						"Manifest line " + lineNo + " must have 4 or 5 comma-separated columns: '" + raw + "'" );
			}
			String modelId = tokens[0].trim();
			int entityCount = Integer.parseInt( tokens[1].trim() );
			int fieldCount = Integer.parseInt( tokens[2].trim() );
			int depth = Integer.parseInt( tokens[3].trim() );
			int seed = tokens.length == 5 ? Integer.parseInt( tokens[4].trim() ) : DEFAULT_SEED;
			specs.add( new ModelSpec( modelId, entityCount, fieldCount, depth, seed ) );
		}
		return specs;
	}
}
