/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.baseline;

import org.hibernate.accessor.HibernateAccessorMultiValueReader;
import org.hibernate.accessor.HibernateAccessorMultiValueWriter;
import org.hibernate.accessor.performance.entities.WideEntity;

/**
 * Hand-written multi-value baseline for {@link WideEntity}, reading/writing the first
 * {@code count} members. The interface-dispatch and raw/inlined baselines both use these
 * {@code final} classes (via the interface type and the concrete type respectively).
 */
public final class DirectWideAccessors {

	private DirectWideAccessors() {
	}

	public static final class WideReader implements HibernateAccessorMultiValueReader {
		private final int count;

		public WideReader(int count) {
			this.count = count;
		}

		@Override
		public Object[] get(Object instance) {
			WideEntity e = (WideEntity) instance;
			Object[] out = new Object[count];
			if ( count > 0 ) {
				out[0] = e.getField0();
			}
			if ( count > 1 ) {
				out[1] = e.getField1();
			}
			if ( count > 2 ) {
				out[2] = e.getField2();
			}
			if ( count > 3 ) {
				out[3] = e.getField3();
			}
			if ( count > 4 ) {
				out[4] = e.getField4();
			}
			if ( count > 5 ) {
				out[5] = e.getField5();
			}
			if ( count > 6 ) {
				out[6] = e.getField6();
			}
			if ( count > 7 ) {
				out[7] = e.getField7();
			}
			if ( count > 8 ) {
				out[8] = e.getField8();
			}
			if ( count > 9 ) {
				out[9] = e.getField9();
			}
			if ( count > 10 ) {
				out[10] = e.getField10();
			}
			if ( count > 11 ) {
				out[11] = e.getField11();
			}
			if ( count > 12 ) {
				out[12] = e.getField12();
			}
			if ( count > 13 ) {
				out[13] = e.getField13();
			}
			if ( count > 14 ) {
				out[14] = e.getField14();
			}
			if ( count > 15 ) {
				out[15] = e.getField15();
			}
			if ( count > 16 ) {
				out[16] = e.getField16();
			}
			if ( count > 17 ) {
				out[17] = e.getField17();
			}
			if ( count > 18 ) {
				out[18] = e.getField18();
			}
			if ( count > 19 ) {
				out[19] = e.getField19();
			}
			if ( count > 20 ) {
				out[20] = e.getField20();
			}
			if ( count > 21 ) {
				out[21] = e.getField21();
			}
			if ( count > 22 ) {
				out[22] = e.getField22();
			}
			if ( count > 23 ) {
				out[23] = e.getField23();
			}
			if ( count > 24 ) {
				out[24] = e.getField24();
			}
			if ( count > 25 ) {
				out[25] = e.getField25();
			}
			if ( count > 26 ) {
				out[26] = e.getField26();
			}
			if ( count > 27 ) {
				out[27] = e.getField27();
			}
			if ( count > 28 ) {
				out[28] = e.getField28();
			}
			if ( count > 29 ) {
				out[29] = e.getField29();
			}
			if ( count > 30 ) {
				out[30] = e.getField30();
			}
			if ( count > 31 ) {
				out[31] = e.getField31();
			}
			return out;
		}
	}

	public static final class WideWriter implements HibernateAccessorMultiValueWriter {
		private final int count;

		public WideWriter(int count) {
			this.count = count;
		}

		@Override
		public void set(Object instance, Object[] values) {
			WideEntity e = (WideEntity) instance;
			if ( count > 0 ) {
				e.setField0( (Integer) values[0] );
			}
			if ( count > 1 ) {
				e.setField1( (String) values[1] );
			}
			if ( count > 2 ) {
				e.setField2( (Integer) values[2] );
			}
			if ( count > 3 ) {
				e.setField3( (String) values[3] );
			}
			if ( count > 4 ) {
				e.setField4( (Integer) values[4] );
			}
			if ( count > 5 ) {
				e.setField5( (String) values[5] );
			}
			if ( count > 6 ) {
				e.setField6( (Integer) values[6] );
			}
			if ( count > 7 ) {
				e.setField7( (String) values[7] );
			}
			if ( count > 8 ) {
				e.setField8( (Integer) values[8] );
			}
			if ( count > 9 ) {
				e.setField9( (String) values[9] );
			}
			if ( count > 10 ) {
				e.setField10( (Integer) values[10] );
			}
			if ( count > 11 ) {
				e.setField11( (String) values[11] );
			}
			if ( count > 12 ) {
				e.setField12( (Integer) values[12] );
			}
			if ( count > 13 ) {
				e.setField13( (String) values[13] );
			}
			if ( count > 14 ) {
				e.setField14( (Integer) values[14] );
			}
			if ( count > 15 ) {
				e.setField15( (String) values[15] );
			}
			if ( count > 16 ) {
				e.setField16( (Integer) values[16] );
			}
			if ( count > 17 ) {
				e.setField17( (String) values[17] );
			}
			if ( count > 18 ) {
				e.setField18( (Integer) values[18] );
			}
			if ( count > 19 ) {
				e.setField19( (String) values[19] );
			}
			if ( count > 20 ) {
				e.setField20( (Integer) values[20] );
			}
			if ( count > 21 ) {
				e.setField21( (String) values[21] );
			}
			if ( count > 22 ) {
				e.setField22( (Integer) values[22] );
			}
			if ( count > 23 ) {
				e.setField23( (String) values[23] );
			}
			if ( count > 24 ) {
				e.setField24( (Integer) values[24] );
			}
			if ( count > 25 ) {
				e.setField25( (String) values[25] );
			}
			if ( count > 26 ) {
				e.setField26( (Integer) values[26] );
			}
			if ( count > 27 ) {
				e.setField27( (String) values[27] );
			}
			if ( count > 28 ) {
				e.setField28( (Integer) values[28] );
			}
			if ( count > 29 ) {
				e.setField29( (String) values[29] );
			}
			if ( count > 30 ) {
				e.setField30( (Integer) values[30] );
			}
			if ( count > 31 ) {
				e.setField31( (String) values[31] );
			}
		}
	}
}
