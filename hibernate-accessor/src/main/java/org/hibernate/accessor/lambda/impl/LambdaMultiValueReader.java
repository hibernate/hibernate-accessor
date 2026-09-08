/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.lambda.impl;

import org.hibernate.accessor.MultiValueReader;
import org.hibernate.accessor.ValueReader;

public class LambdaMultiValueReader implements MultiValueReader {

	private final ValueReader<?>[] readers;

	public LambdaMultiValueReader(ValueReader<?>[] readers) {
		this.readers = readers;
	}

	@Override
	public Object[] get(Object instance) {
		final Object[] values = new Object[readers.length];
		for ( int i = 0; i < readers.length; i++ ) {
			values[i] = readers[i].get( instance );
		}
		return values;
	}
}
