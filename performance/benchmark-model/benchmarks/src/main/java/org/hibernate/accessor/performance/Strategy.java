/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Map;

import org.hibernate.accessor.HibernateAccessorFactory;
import org.hibernate.accessor.spi.HibernateAccessorConfiguration;

/**
 * The accessor strategies under test, mapped to their {@link HibernateAccessorFactory}.
 *
 * <p><strong>Duplicated</strong> verbatim in the {@code hibernate-accessor-benchmark-basic} and
 * {@code hibernate-accessor-benchmark-model} modules (the two never share a classpath). Keep the
 * copies in sync.
 *
 * <p>The reflection, method-handle and lambda strategies live in the core module and are
 * referenced directly. The ASM and ByteBuddy strategies live in separate modules that are
 * only present on the classpath of their respective benchmark jars, so they are resolved
 * reflectively; requesting them from a jar that does not bundle them fails fast.
 *
 * <p>The {@code *_PER_MEMBER} variants request the same ASM/ByteBuddy factory but configured to
 * generate one dedicated class per member (rather than a per-entity bulk accessor with switch
 * dispatch), so a run can compare the two code-generation strategies side by side.
 */
public enum Strategy {

	REFLECTION {
		@Override
		public HibernateAccessorFactory create(MethodHandles.Lookup lookup) {
			return HibernateAccessorFactory.reflection();
		}
	},
	METHOD_HANDLE {
		@Override
		public HibernateAccessorFactory create(MethodHandles.Lookup lookup) {
			return HibernateAccessorFactory.methodHandle( lookup );
		}
	},
	LAMBDA {
		@Override
		public HibernateAccessorFactory create(MethodHandles.Lookup lookup) {
			return HibernateAccessorFactory.lambda( lookup );
		}
	},
	ASM {
		@Override
		public HibernateAccessorFactory create(MethodHandles.Lookup lookup) {
			return reflectiveFactory( name(), "org.hibernate.accessor.asm.HibernateAccessorAsmFactory", lookup );
		}
	},
	BYTE_BUDDY {
		@Override
		public HibernateAccessorFactory create(MethodHandles.Lookup lookup) {
			return reflectiveFactory( name(), "org.hibernate.accessor.bytebuddy.HibernateAccessorByteBuddyFactory", lookup );
		}
	},
	ASM_PER_MEMBER {
		@Override
		public HibernateAccessorFactory create(MethodHandles.Lookup lookup) {
			return reflectiveFactory( name(), "org.hibernate.accessor.asm.HibernateAccessorAsmFactory", perMemberConfig( lookup ) );
		}
	},
	BYTE_BUDDY_PER_MEMBER {
		@Override
		public HibernateAccessorFactory create(MethodHandles.Lookup lookup) {
			return reflectiveFactory( name(), "org.hibernate.accessor.bytebuddy.HibernateAccessorByteBuddyFactory", perMemberConfig( lookup ) );
		}
	},
	/**
	 * The build-time double-switch: a single shared reader dispatches {@code get()} via
	 * {@code tableswitch(classIndex)} into an entity host method that in turn does
	 * {@code tableswitch(memberIndex)} to a direct field/getter read. It is whole-model (the shared
	 * reader is generated over all types at once) rather than per-{@code Field}, so it has no
	 * {@link HibernateAccessorFactory}; {@code GeneratedGraphBenchmark} constructs its readers
	 * directly from the generated model. Only exercised by that benchmark.
	 */
	GENERATED_DOUBLE_SWITCH {
		@Override
		public HibernateAccessorFactory create(MethodHandles.Lookup lookup) {
			throw new UnsupportedOperationException(
					"GENERATED_DOUBLE_SWITCH is whole-model and has no per-member factory; "
							+ "GeneratedGraphBenchmark builds its readers from the generated model." );
		}
	};

	/**
	 * Creates the factory for this strategy.
	 *
	 * @param lookup a full-privilege lookup with access to the benchmark entities
	 * @return the factory implementing this strategy
	 */
	public abstract HibernateAccessorFactory create(MethodHandles.Lookup lookup);

	private static HibernateAccessorFactory reflectiveFactory(
			String strategyName, String className, MethodHandles.Lookup lookup) {
		try {
			Class<?> factoryClass = Class.forName( className );
			Method factoryMethod = factoryClass.getMethod( "factory", MethodHandles.Lookup.class );
			return (HibernateAccessorFactory) factoryMethod.invoke( null, lookup );
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(
					"Strategy '" + strategyName + "' is not available on the classpath (" + className
							+ "). Run the matching benchmark jar.",
					e );
		}
	}

	private static HibernateAccessorFactory reflectiveFactory(
			String strategyName, String className, HibernateAccessorConfiguration configuration) {
		try {
			Class<?> factoryClass = Class.forName( className );
			Method factoryMethod = factoryClass.getMethod( "factory", HibernateAccessorConfiguration.class );
			return (HibernateAccessorFactory) factoryMethod.invoke( null, configuration );
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(
					"Strategy '" + strategyName + "' is not available on the classpath (" + className
							+ "). Run the matching benchmark jar.",
					e );
		}
	}

	// The generation-strategy property is carried as a plain string so the core benchmark module
	// need not depend on the ASM/ByteBuddy module that declares the key/enum; the factory resolves
	// the string back to its generation-strategy enum.
	private static HibernateAccessorConfiguration perMemberConfig(MethodHandles.Lookup lookup) {
		return new HibernateAccessorConfiguration( lookup, Map.of( "hibernate.accessor.generation.strategy", "PER_MEMBER" ) );
	}
}
