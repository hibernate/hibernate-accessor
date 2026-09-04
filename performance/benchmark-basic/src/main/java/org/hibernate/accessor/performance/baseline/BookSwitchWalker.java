/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.baseline;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.accessor.performance.entities.book.BookSchema;
import org.hibernate.accessor.performance.entities.book.BookSchema.Member;

/**
 * The cascade traversal engine for the two-layer-switch reference, structurally identical to
 * {@code CascadeWalker#visit} (same per-type {@code plans.get(bean.getClass())} lookup, same
 * scalar-then-cascade order, same accumulation) but reading each member through the single
 * monomorphic {@link BookSwitchDispatcher#get} call instead of a per-member reader object.
 *
 * <p>Keeping the walk shape identical means the delta against {@code ifaceCascade} / the per-member
 * strategies isolates exactly the dispatch mechanism (one monomorphic call + two switches vs. a
 * megamorphic virtual call to a tiny method).
 */
public final class BookSwitchWalker {

	/** For one entity type: its class id, and the member ids to read as scalars vs. follow as cascades. */
	private record SwitchPlan(int classId, int[] scalarIds, int[] cascadeIds) {
	}

	private final Map<Class<?>, SwitchPlan> plans;

	private BookSwitchWalker(Map<Class<?>, SwitchPlan> plans) {
		this.plans = plans;
	}

	/**
	 * Builds the walker from {@link BookSchema}, using the same class-id order ({@code ENTITIES}) and
	 * per-type member-id order ({@code SCHEMA}) that {@link BookSwitchDispatcher} switches on.
	 */
	public static BookSwitchWalker create() {
		Map<Class<?>, SwitchPlan> plans = new HashMap<>();
		for ( int classId = 0; classId < BookSchema.ENTITIES.size(); classId++ ) {
			Class<?> type = BookSchema.ENTITIES.get( classId );
			List<Member> members = BookSchema.SCHEMA.get( type );

			int[] scalarIds = new int[(int) members.stream().filter( m -> !m.cascade() ).count()];
			int[] cascadeIds = new int[members.size() - scalarIds.length];
			int s = 0;
			int c = 0;
			for ( int memberId = 0; memberId < members.size(); memberId++ ) {
				if ( members.get( memberId ).cascade() ) {
					cascadeIds[c++] = memberId;
				}
				else {
					scalarIds[s++] = memberId;
				}
			}
			plans.put( type, new SwitchPlan( classId, scalarIds, cascadeIds ) );
		}
		return new BookSwitchWalker( plans );
	}

	/** Walks the graph from {@code root}, folding every scalar value into a single accumulator. */
	public long walk(Object root) {
		return visit( root, 0L );
	}

	private long visit(Object bean, long acc) {
		if ( bean == null ) {
			return acc;
		}
		SwitchPlan plan = plans.get( bean.getClass() );
		if ( plan == null ) {
			return acc;
		}
		int classId = plan.classId();
		for ( int memberId : plan.scalarIds() ) {
			Object value = BookSwitchDispatcher.get( classId, memberId, bean );
			if ( value != null ) {
				acc += value.hashCode();
			}
		}
		for ( int memberId : plan.cascadeIds() ) {
			Object next = BookSwitchDispatcher.get( classId, memberId, bean );
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
