/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance;

import java.lang.invoke.MethodHandles;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.accessor.HibernateAccessorFactory;
import org.hibernate.accessor.performance.model.GeneratedModel;

/**
 * Reports the memory footprint a strategy pays to build all readers for one model: how many classes
 * it loads (near zero for reflection/method-handle, one-per-entity for bulk ASM, one-per-member for
 * per-member codegen) and how much metaspace that costs.
 *
 * <p>Not a JMH benchmark -- footprint is a whole-JVM property, so this is meant to be launched in a
 * <em>fresh JVM per (strategy, model, access)</em> for a clean baseline:
 *
 * <pre>{@code
 * java -cp hibernate-accessor-performance-asm.jar \
 *   org.hibernate.accessor.performance.FootprintReport ASM e256_f16_d2 FIELD
 * }</pre>
 *
 * <p>Prints one CSV row: {@code strategy,modelId,access,readers,classesLoaded,metaspaceBytes}. The
 * class-count delta is the reliable signal; the metaspace delta is indicative (other classes may load
 * concurrently), so compare it across strategies rather than reading it as an absolute.
 */
public final class FootprintReport {

	private FootprintReport() {
	}

	public static void main(String[] args) {
		if ( args.length != 3 ) {
			System.err.println( "Usage: FootprintReport <strategy> <modelId> <FIELD|METHOD>" );
			System.exit( 1 );
		}
		Strategy strategy = Strategy.valueOf( args[0] );
		String modelId = args[1];
		AccessKind access = AccessKind.valueOf( args[2] );

		GeneratedModel model = new GeneratedModel( modelId );
		int entityCount = model.entityCount();
		int fieldCount = model.fieldCount();
		int depth = model.depth();

		ClassLoadingMXBean classLoading = ManagementFactory.getClassLoadingMXBean();
		MemoryPoolMXBean metaspace = metaspacePool();

		long classesBefore = classLoading.getTotalLoadedClassCount();
		long metaBefore = usedMetaspace( metaspace );

		HibernateAccessorFactory factory = strategy.create( MethodHandles.lookup() );
		boolean field = access == AccessKind.FIELD;

		// Hold references so the generated classes/readers cannot be unloaded before measuring.
		List<Object> readers = new ArrayList<>( entityCount * ( fieldCount + depth ) );
		for ( int t = 0; t < entityCount; t++ ) {
			for ( int i = 0; i < fieldCount; i++ ) {
				readers.add( field
						? factory.valueReader( model.scalarField( t, i ) )
						: factory.valueReader( model.scalarGetter( t, i ) ) );
			}
			for ( int j = 0; j < depth; j++ ) {
				readers.add( field
						? factory.valueReader( model.referenceField( t, j ) )
						: factory.valueReader( model.referenceGetter( t, j ) ) );
			}
		}

		long classesAfter = classLoading.getTotalLoadedClassCount();
		long metaAfter = usedMetaspace( metaspace );

		System.out.printf(
				"%s,%s,%s,%d,%d,%d%n",
				strategy, modelId, access, readers.size(),
				classesAfter - classesBefore, metaAfter - metaBefore );

		// Touch the list so JIT/GC cannot treat the readers as dead before the measurement above.
		if ( readers.isEmpty() ) {
			throw new IllegalStateException( "no readers built" );
		}
	}

	private static MemoryPoolMXBean metaspacePool() {
		for ( MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans() ) {
			if ( "Metaspace".equals( pool.getName() ) ) {
				return pool;
			}
		}
		return null;
	}

	private static long usedMetaspace(MemoryPoolMXBean metaspace) {
		return metaspace == null ? -1 : metaspace.getUsage().getUsed();
	}
}
