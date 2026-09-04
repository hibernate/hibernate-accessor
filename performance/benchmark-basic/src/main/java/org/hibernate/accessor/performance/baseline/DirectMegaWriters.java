/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.baseline;

import org.hibernate.accessor.HibernateAccessorValueWriter;
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

/** Hand-written value writers for each distinct megamorphic entity type. */
public final class DirectMegaWriters {

	private DirectMegaWriters() {
	}

	/** One writer per {@code MegaEntities.CLASSES} entry, in the same order. */
	public static HibernateAccessorValueWriter[] writers() {
		return new HibernateAccessorValueWriter[] {
				new W00(),
				new W01(),
				new W02(),
				new W03(),
				new W04(),
				new W05(),
				new W06(),
				new W07(),
				new W08(),
				new W09(),
				new W10(),
				new W11()
		};
	}

	static final class W00 implements HibernateAccessorValueWriter {
		@Override
		public void set(Object instance, Object value) {
			( (Entity00) instance ).setValue( (Integer) value );
		}
	}

	static final class W01 implements HibernateAccessorValueWriter {
		@Override
		public void set(Object instance, Object value) {
			( (Entity01) instance ).setValue( (Integer) value );
		}
	}

	static final class W02 implements HibernateAccessorValueWriter {
		@Override
		public void set(Object instance, Object value) {
			( (Entity02) instance ).setValue( (Integer) value );
		}
	}

	static final class W03 implements HibernateAccessorValueWriter {
		@Override
		public void set(Object instance, Object value) {
			( (Entity03) instance ).setValue( (Integer) value );
		}
	}

	static final class W04 implements HibernateAccessorValueWriter {
		@Override
		public void set(Object instance, Object value) {
			( (Entity04) instance ).setValue( (Integer) value );
		}
	}

	static final class W05 implements HibernateAccessorValueWriter {
		@Override
		public void set(Object instance, Object value) {
			( (Entity05) instance ).setValue( (Integer) value );
		}
	}

	static final class W06 implements HibernateAccessorValueWriter {
		@Override
		public void set(Object instance, Object value) {
			( (Entity06) instance ).setValue( (Integer) value );
		}
	}

	static final class W07 implements HibernateAccessorValueWriter {
		@Override
		public void set(Object instance, Object value) {
			( (Entity07) instance ).setValue( (Integer) value );
		}
	}

	static final class W08 implements HibernateAccessorValueWriter {
		@Override
		public void set(Object instance, Object value) {
			( (Entity08) instance ).setValue( (Integer) value );
		}
	}

	static final class W09 implements HibernateAccessorValueWriter {
		@Override
		public void set(Object instance, Object value) {
			( (Entity09) instance ).setValue( (Integer) value );
		}
	}

	static final class W10 implements HibernateAccessorValueWriter {
		@Override
		public void set(Object instance, Object value) {
			( (Entity10) instance ).setValue( (Integer) value );
		}
	}

	static final class W11 implements HibernateAccessorValueWriter {
		@Override
		public void set(Object instance, Object value) {
			( (Entity11) instance ).setValue( (Integer) value );
		}
	}
}
