/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.generator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Build-time entry point: reads the manifest and emits, per model, the entity {@code .class} files
 * plus a {@code models/<modelId>.properties} descriptor into the given output directory.
 *
 * <p>The output directory is placed on the {@code performance} module's runtime classpath, so the
 * descriptors resolve as classpath resources ({@code models/<modelId>.properties}) and the entities
 * load by their fully-qualified names.
 *
 * <p>Usage: {@code GenerateModelsMain <manifest-file> <output-dir>}
 */
public final class GenerateModelsMain {

	private GenerateModelsMain() {
	}

	public static void main(String[] args) throws IOException {
		if ( args.length != 2 ) {
			throw new IllegalArgumentException( "Usage: GenerateModelsMain <manifest-file> <output-dir>" );
		}
		Path manifest = Path.of( args[0] );
		Path outputDir = Path.of( args[1] );

		List<ModelSpec> specs = ManifestParser.parse( manifest );
		Files.createDirectories( outputDir.resolve( "models" ) );

		for ( ModelSpec spec : specs ) {
			emitModel( spec, outputDir );
			System.out.printf(
					"Generated model %-14s (entities=%d, fields=%d, depth=%d)%n",
					spec.modelId(), spec.entityCount(), spec.fieldCount(), spec.depth() );
		}
		System.out.printf( "Emitted %d model(s) into %s%n", specs.size(), outputDir.toAbsolutePath() );
	}

	private static void emitModel(ModelSpec spec, Path outputDir) {
		String packageInternal = spec.packageName().replace( '.', '/' );
		Path packageDir = outputDir.resolve( packageInternal );
		try {
			Files.createDirectories( packageDir );
			for ( int t = 0; t < spec.entityCount(); t++ ) {
				byte[] bytes = EntityClassEmitter.emit(
						packageInternal, t, spec.fieldCount(), spec.depth(), spec.entityCount() );
				Files.write( packageDir.resolve( "E" + t + ".class" ), bytes );
			}

			// The two shared double-switch readers (field-direct and getter-based).
			Files.write(
					packageDir.resolve( GeneratedNames.READER_FIELD_SIMPLE + ".class" ),
					SwitchReaderEmitter.emit(
							packageInternal, GeneratedNames.READER_FIELD_SIMPLE,
							GeneratedNames.READ_METHOD_FIELD, spec.entityCount() ) );
			Files.write(
					packageDir.resolve( GeneratedNames.READER_METHOD_SIMPLE + ".class" ),
					SwitchReaderEmitter.emit(
							packageInternal, GeneratedNames.READER_METHOD_SIMPLE,
							GeneratedNames.READ_METHOD_GETTER, spec.entityCount() ) );

			writeDescriptor( spec, outputDir );
		}
		catch (IOException e) {
			throw new UncheckedIOException( "Failed to emit model '" + spec.modelId() + "'", e );
		}
	}

	private static void writeDescriptor(ModelSpec spec, Path outputDir) throws IOException {
		String descriptor = ""
				+ "modelId=" + spec.modelId() + "\n"
				+ "package=" + spec.packageName() + "\n"
				+ "entityCount=" + spec.entityCount() + "\n"
				+ "fieldCount=" + spec.fieldCount() + "\n"
				+ "depth=" + spec.depth() + "\n"
				+ "seed=" + spec.seed() + "\n"
				+ "readerFieldClass=" + spec.packageName() + "." + GeneratedNames.READER_FIELD_SIMPLE + "\n"
				+ "readerMethodClass=" + spec.packageName() + "." + GeneratedNames.READER_METHOD_SIMPLE + "\n";
		Path descriptorFile = outputDir.resolve( "models" ).resolve( spec.modelId() + ".properties" );
		Files.writeString( descriptorFile, descriptor, StandardCharsets.UTF_8 );
	}
}
