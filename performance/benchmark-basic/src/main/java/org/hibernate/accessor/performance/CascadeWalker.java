/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.accessor.HibernateAccessorFactory;
import org.hibernate.accessor.HibernateAccessorValueReader;
import org.hibernate.accessor.performance.entities.book.BookSchema;
import org.hibernate.accessor.performance.entities.book.BookSchema.Member;

/**
 * A minimal cascading-traversal engine, shared by {@code CascadeBenchmark} and its baseline.
 *
 * <p>It models what a validator or an ORM dirty-check does: for every bean it reaches, read the scalar
 * properties (the leaves) and follow the reference/collection properties (the cascades) into the next
 * beans. Every read goes through {@link HibernateAccessorValueReader#get} at the two loops below, so a
 * whole graph of distinct entity types funnels through two shared, megamorphic call sites -- the real
 * workload shape the microbenchmarks approximate.
 */
public final class CascadeWalker {

	/** The readers for one concrete entity type: scalar leaves to read, cascade edges to follow. */
	public record TypePlan(HibernateAccessorValueReader<?>[] scalars, HibernateAccessorValueReader<?>[] cascades) {
	}

	private final Map<Class<?>, TypePlan> plans;

	public CascadeWalker(Map<Class<?>, TypePlan> plans) {
		this.plans = plans;
	}

	/**
	 * Wires a walker for a strategy, reading each property by field, by getter, or a mix, per the
	 * {@link BookSchema}.
	 */
	public static CascadeWalker forStrategy(HibernateAccessorFactory factory, CascadeAccess access)
			throws ReflectiveOperationException {
		Map<Class<?>, TypePlan> plans = new HashMap<>();
		for ( Class<?> type : BookSchema.ENTITIES ) {
			List<Member> members = BookSchema.SCHEMA.get( type );
			List<HibernateAccessorValueReader<?>> scalars = new ArrayList<>();
			List<HibernateAccessorValueReader<?>> cascades = new ArrayList<>();
			for ( int i = 0; i < members.size(); i++ ) {
				Member m = members.get( i );
				boolean useField = switch ( access ) {
					case FIELD -> true;
					case METHOD -> false;
					// MIXED: alternate field/getter across a type's properties, so a single traversal
					// exercises both access shapes the way a real metamodel (some fields, some getters) would.
					case MIXED -> i % 2 == 0;
				};
				HibernateAccessorValueReader<?> reader = useField
						? factory.valueReader( type.getDeclaredField( m.field() ) )
						: factory.valueReader( type.getMethod( m.getter() ) );
				( m.cascade() ? cascades : scalars ).add( reader );
			}
			plans.put( type, new TypePlan(
					scalars.toArray( new HibernateAccessorValueReader<?>[0] ),
					cascades.toArray( new HibernateAccessorValueReader<?>[0] ) ) );
		}
		return new CascadeWalker( plans );
	}

	/** Walks the graph from {@code root}, folding every scalar value into a single accumulator. */
	public long walk(Object root) {
		return visit( root, 0L );
	}

	private long visit(Object bean, long acc) {
		if ( bean == null ) {
			return acc;
		}
		TypePlan plan = plans.get( bean.getClass() );
		if ( plan == null ) {
			return acc;
		}
		for ( HibernateAccessorValueReader<?> reader : plan.scalars() ) {
			Object value = reader.get( bean );
			if ( value != null ) {
				acc += value.hashCode();
			}
		}
		for ( HibernateAccessorValueReader<?> reader : plan.cascades() ) {
			Object next = reader.get( bean );
			if ( next instanceof Iterable<?> elements ) {
				for ( Object element : elements ) {
					acc = visit( element, acc );
				}
			}
			else {
				acc = visit( next, acc );
			}
		}
		return acc;
	}
}
