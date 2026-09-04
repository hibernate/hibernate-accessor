/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;

import org.hibernate.accessor.performance.AccessKind;

/**
 * Runtime view over one build-generated model, loaded by {@code modelId} from the classpath.
 *
 * <p>Reads the {@code models/<modelId>.properties} descriptor emitted by the
 * {@code benchmark-model-generator}, resolves the entity classes and their members by naming
 * convention, and builds the deterministic instance graph the benchmark walks:
 * <ul>
 *   <li>{@code entityCount} types {@code E0..E<n>}, one root instance each;</li>
 *   <li>each root's scalar {@code f<i>} set to a seeded value;</li>
 *   <li>each root's reference {@code r<j>} wired to {@code E_((t+1+j) % entityCount)} (root + N leaves).</li>
 * </ul>
 *
 * <p>All member handles are plain reflection here -- this class is strategy-agnostic model/data
 * setup. The accessor strategy under test builds its own readers over the same members and roots.
 */
public final class GeneratedModel {

	private final String modelId;
	private final int entityCount;
	private final int fieldCount;
	private final int depth;
	private final int seed;

	private final Class<?>[] types;
	private final Field[][] scalarFields;
	private final Method[][] scalarGetters;
	private final Field[][] referenceFields;
	private final Method[][] referenceGetters;
	private final int[][] referenceLeafType;

	private final String readerFieldClassName;
	private final String readerMethodClassName;

	private final Object[] roots;
	private final long referenceChecksum;

	public GeneratedModel(String modelId) {
		this.modelId = modelId;

		Properties descriptor = loadDescriptor( modelId );
		String packageName = required( descriptor, "package" );
		this.entityCount = Integer.parseInt( required( descriptor, "entityCount" ) );
		this.fieldCount = Integer.parseInt( required( descriptor, "fieldCount" ) );
		this.depth = Integer.parseInt( required( descriptor, "depth" ) );
		this.seed = Integer.parseInt( required( descriptor, "seed" ) );
		this.readerFieldClassName = required( descriptor, "readerFieldClass" );
		this.readerMethodClassName = required( descriptor, "readerMethodClass" );

		this.types = new Class<?>[entityCount];
		this.scalarFields = new Field[entityCount][fieldCount];
		this.scalarGetters = new Method[entityCount][fieldCount];
		this.referenceFields = new Field[entityCount][depth];
		this.referenceGetters = new Method[entityCount][depth];
		this.referenceLeafType = new int[entityCount][depth];

		try {
			resolveMembers( packageName );
			this.roots = buildGraph();
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException( "Failed to build generated model '" + modelId + "'", e );
		}
		this.referenceChecksum = computeReferenceChecksum();
	}

	private void resolveMembers(String packageName) throws ReflectiveOperationException {
		for ( int t = 0; t < entityCount; t++ ) {
			Class<?> type = Class.forName( packageName + ".E" + t );
			types[t] = type;
			for ( int i = 0; i < fieldCount; i++ ) {
				Field field = type.getDeclaredField( "f" + i );
				field.setAccessible( true );
				scalarFields[t][i] = field;
				scalarGetters[t][i] = type.getMethod( "getF" + i );
			}
			for ( int j = 0; j < depth; j++ ) {
				Field field = type.getDeclaredField( "r" + j );
				field.setAccessible( true );
				referenceFields[t][j] = field;
				referenceGetters[t][j] = type.getMethod( "getR" + j );
				referenceLeafType[t][j] = ( t + 1 + j ) % entityCount;
			}
		}
	}

	private Object[] buildGraph() throws ReflectiveOperationException {
		Object[] instances = new Object[entityCount];
		for ( int t = 0; t < entityCount; t++ ) {
			instances[t] = types[t].getDeclaredConstructor().newInstance();
		}
		for ( int t = 0; t < entityCount; t++ ) {
			for ( int i = 0; i < fieldCount; i++ ) {
				scalarFields[t][i].setInt( instances[t], seededValue( t, i ) );
			}
			for ( int j = 0; j < depth; j++ ) {
				referenceFields[t][j].set( instances[t], instances[referenceLeafType[t][j]] );
			}
		}
		return instances;
	}

	/**
	 * The expected checksum, computed independently via direct reflection, following the exact
	 * traversal and fold {@code GeneratedGraphBenchmark#walk()} uses. Any accessor strategy must
	 * reproduce this value, giving a free per-strategy correctness check.
	 */
	private long computeReferenceChecksum() {
		return expectedChecksum( fieldCount );
	}

	/**
	 * The expected checksum when only the first {@code hotScalarCount} scalars of each type are read,
	 * following the exact traversal and fold {@code GeneratedGraphBenchmark#walk()} uses for the chosen
	 * read mode. References are always followed in full; {@code hotScalarCount == fieldCount} reproduces
	 * {@link #referenceChecksum()}.
	 */
	public long expectedChecksum(int hotScalarCount) {
		try {
			long acc = 1L;
			for ( int t = 0; t < entityCount; t++ ) {
				Object root = roots[t];
				for ( int i = 0; i < hotScalarCount; i++ ) {
					acc = acc * 31 + scalarFields[t][i].getInt( root );
				}
				for ( int j = 0; j < depth; j++ ) {
					int leafType = referenceLeafType[t][j];
					Object leaf = referenceFields[t][j].get( root );
					for ( int i = 0; i < hotScalarCount; i++ ) {
						acc = acc * 31 + scalarFields[leafType][i].getInt( leaf );
					}
				}
			}
			return acc;
		}
		catch (IllegalAccessException e) {
			throw new IllegalStateException( "Failed to compute reference checksum for '" + modelId + "'", e );
		}
	}

	private int seededValue(int typeIndex, int fieldIndex) {
		return typeIndex * 1_000_003 + fieldIndex * 31 + seed;
	}

	private static Properties loadDescriptor(String modelId) {
		String resource = "models/" + modelId + ".properties";
		ClassLoader loader = GeneratedModel.class.getClassLoader();
		try ( InputStream in = loader.getResourceAsStream( resource ) ) {
			if ( in == null ) {
				throw new IllegalArgumentException(
						"No generated model descriptor on the classpath: " + resource
								+ ". Run the 'generateBenchmarkModel' task and rebuild the benchmark jar." );
			}
			Properties properties = new Properties();
			properties.load( in );
			return properties;
		}
		catch (IOException e) {
			throw new UncheckedIOException( "Failed to read descriptor for model '" + modelId + "'", e );
		}
	}

	private static String required(Properties properties, String key) {
		String value = properties.getProperty( key );
		if ( value == null ) {
			throw new IllegalStateException( "Descriptor is missing required key '" + key + "'" );
		}
		return value.trim();
	}

	public String modelId() {
		return modelId;
	}

	public int entityCount() {
		return entityCount;
	}

	public int fieldCount() {
		return fieldCount;
	}

	public int depth() {
		return depth;
	}

	public Field scalarField(int typeIndex, int fieldIndex) {
		return scalarFields[typeIndex][fieldIndex];
	}

	public Method scalarGetter(int typeIndex, int fieldIndex) {
		return scalarGetters[typeIndex][fieldIndex];
	}

	public Field referenceField(int typeIndex, int refIndex) {
		return referenceFields[typeIndex][refIndex];
	}

	public Method referenceGetter(int typeIndex, int refIndex) {
		return referenceGetters[typeIndex][refIndex];
	}

	public int referenceLeafType(int typeIndex, int refIndex) {
		return referenceLeafType[typeIndex][refIndex];
	}

	public Object[] roots() {
		return roots;
	}

	/**
	 * The shared double-switch reader class for the given access kind. Instantiate it with the
	 * {@code (int classIndex, int memberIndex)} constructor to obtain a reader for a specific member;
	 * every reader is an instance of this one class, keeping the {@code get()} call site monomorphic.
	 */
	public Class<?> switchReaderClass(AccessKind access) {
		String className = access == AccessKind.FIELD ? readerFieldClassName : readerMethodClassName;
		try {
			return Class.forName( className );
		}
		catch (ClassNotFoundException e) {
			throw new IllegalStateException( "Missing generated reader class '" + className + "'", e );
		}
	}

	public long referenceChecksum() {
		return referenceChecksum;
	}
}
