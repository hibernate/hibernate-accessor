/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.baseline;

import org.hibernate.accessor.HibernateAccessorValueReader;
import org.hibernate.accessor.performance.entities.mega.Entity00;
import org.hibernate.accessor.performance.entities.mega.Entity01;
import org.hibernate.accessor.performance.entities.mega.Entity02;
import org.hibernate.accessor.performance.entities.mega.Entity03;
import org.hibernate.accessor.performance.entities.mega.Entity04;
import org.hibernate.accessor.performance.entities.mega.Entity05;
import org.hibernate.accessor.performance.entities.mega.Entity06;
import org.hibernate.accessor.performance.entities.mega.Entity07;
import org.hibernate.accessor.performance.entities.mega.Entity08;
import org.hibernate.accessor.performance.entities.mega.Entity09;
import org.hibernate.accessor.performance.entities.mega.Entity10;
import org.hibernate.accessor.performance.entities.mega.Entity11;

/** Hand-written value readers for each distinct megamorphic entity type. */
public final class DirectMegaReaders {

	private DirectMegaReaders() {
	}

	/** One reader per {@code MegaEntities.CLASSES} entry, in the same order. */
	public static HibernateAccessorValueReader<?>[] readers() {
		return new HibernateAccessorValueReader<?>[] {
				new R00(),
				new R01(),
				new R02(),
				new R03(),
				new R04(),
				new R05(),
				new R06(),
				new R07(),
				new R08(),
				new R09(),
				new R10(),
				new R11()
		};
	}

	static final class R00 implements HibernateAccessorValueReader<Integer> {
		@Override
		public Integer get(Object instance) {
			return ( (Entity00) instance ).getValue();
		}
	}

	static final class R01 implements HibernateAccessorValueReader<Integer> {
		@Override
		public Integer get(Object instance) {
			return ( (Entity01) instance ).getValue();
		}
	}

	static final class R02 implements HibernateAccessorValueReader<Integer> {
		@Override
		public Integer get(Object instance) {
			return ( (Entity02) instance ).getValue();
		}
	}

	static final class R03 implements HibernateAccessorValueReader<Integer> {
		@Override
		public Integer get(Object instance) {
			return ( (Entity03) instance ).getValue();
		}
	}

	static final class R04 implements HibernateAccessorValueReader<Integer> {
		@Override
		public Integer get(Object instance) {
			return ( (Entity04) instance ).getValue();
		}
	}

	static final class R05 implements HibernateAccessorValueReader<Integer> {
		@Override
		public Integer get(Object instance) {
			return ( (Entity05) instance ).getValue();
		}
	}

	static final class R06 implements HibernateAccessorValueReader<Integer> {
		@Override
		public Integer get(Object instance) {
			return ( (Entity06) instance ).getValue();
		}
	}

	static final class R07 implements HibernateAccessorValueReader<Integer> {
		@Override
		public Integer get(Object instance) {
			return ( (Entity07) instance ).getValue();
		}
	}

	static final class R08 implements HibernateAccessorValueReader<Integer> {
		@Override
		public Integer get(Object instance) {
			return ( (Entity08) instance ).getValue();
		}
	}

	static final class R09 implements HibernateAccessorValueReader<Integer> {
		@Override
		public Integer get(Object instance) {
			return ( (Entity09) instance ).getValue();
		}
	}

	static final class R10 implements HibernateAccessorValueReader<Integer> {
		@Override
		public Integer get(Object instance) {
			return ( (Entity10) instance ).getValue();
		}
	}

	static final class R11 implements HibernateAccessorValueReader<Integer> {
		@Override
		public Integer get(Object instance) {
			return ( (Entity11) instance ).getValue();
		}
	}
}
