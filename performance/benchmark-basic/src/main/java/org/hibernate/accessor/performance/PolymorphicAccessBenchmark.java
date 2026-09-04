/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.hibernate.accessor.HibernateAccessorFactory;
import org.hibernate.accessor.HibernateAccessorValueReader;
import org.hibernate.accessor.performance.entities.mega.MegaEntities;

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
 * The megamorphic call site, split by {@link AccessKind field vs getter access} -- the scenario where
 * a code-generating strategy (ASM/ByteBuddy) is expected to beat the JDK strategies.
 *
 * <p>{@link MegamorphicBenchmark} only exercises getter access; this one adds the field axis, which is
 * the interesting one: {@code LambdaMetafactory} can bind a real lambda to a getter <em>method</em> but
 * not to a <em>field</em>, so the lambda strategy falls back to a method-handle-shaped path for fields.
 * At a megamorphic call site nothing inlines, so the winner is decided by what each {@code get()} body
 * does once dispatched:
 * <ul>
 *   <li><b>METHOD</b> -- lambda's body is a plain getter call; a generated accessor's body is a record
 *       hop plus a {@code tableswitch}. Lambda ties or beats ASM here.</li>
 *   <li><b>FIELD</b> -- lambda/method-handle megamorphically invoke a handle; a generated accessor does
 *       a direct {@code getfield} after a {@code checkcast}. This is where ASM should shine.</li>
 * </ul>
 * The pairing motivates a mixed factory: lambda for getters, generated code only for fields.
 *
 * <p>With {@code polymorphic=false} the same accessor class and receiver type are used throughout
 * (monomorphic, everything inlines); with {@code polymorphic=true} each element uses a distinct
 * accessor class and receiver type (megamorphic, inlining defeated). Each invocation performs one read
 * per {@link MegaEntities#CLASSES entry}.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class PolymorphicAccessBenchmark {

	@Param({ "REFLECTION", "METHOD_HANDLE", "LAMBDA" })
	private Strategy strategy;

	@Param({ "FIELD", "METHOD" })
	private AccessKind access;

	@Param({ "false", "true" })
	private boolean polymorphic;

	private HibernateAccessorValueReader<?>[] readers;
	private Object[] instances;

	@Setup
	public void setUp() throws ReflectiveOperationException {
		HibernateAccessorFactory factory = strategy.create( MethodHandles.lookup() );
		List<Class<?>> classes = MegaEntities.CLASSES;
		int n = classes.size();
		this.readers = new HibernateAccessorValueReader<?>[n];
		this.instances = new Object[n];
		for ( int i = 0; i < n; i++ ) {
			Class<?> type = polymorphic ? classes.get( i ) : classes.get( 0 );
			this.instances[i] = type.getDeclaredConstructor( int.class ).newInstance( i );
			this.readers[i] = access == AccessKind.FIELD
					? factory.valueReader( type.getDeclaredField( "value" ) )
					: factory.valueReader( type.getMethod( "getValue" ) );
		}
	}

	@Benchmark
	public void polymorphicRead(Blackhole bh) {
		HibernateAccessorValueReader<?>[] rs = this.readers;
		Object[] is = this.instances;
		for ( int i = 0; i < rs.length; i++ ) {
			bh.consume( rs[i].get( is[i] ) );
		}
	}
}
