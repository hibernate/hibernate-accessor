/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.spi;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;

public class AccessorConfiguration {

	public static final AccessorConfiguration EMPTY = new AccessorConfiguration( Map.of() );

	public static final String LOOKUP = "hibernate.accessor.lookup";
	public static final String DUMP_BYTECODE_DIR = "hibernate.accessor.bytecode.dump.dir";

	private final Map<String, Object> properties;

	public AccessorConfiguration(Map<String, Object> properties) {
		this.properties = properties;
	}

	public AccessorConfiguration(MethodHandles.Lookup lookup) {
		this( Map.of( LOOKUP, lookup ) );
	}

	public AccessorConfiguration(MethodHandles.Lookup lookup, Map<String, Object> properties) {
		final Map<String, Object> merged = new HashMap<>( properties );
		merged.put( LOOKUP, lookup );
		this.properties = merged;
	}

	public MethodHandles.Lookup lookup() {
		return getProperty( LOOKUP, MethodHandles.Lookup.class );
	}

	@SuppressWarnings("unchecked")
	public <T> T getProperty(String name, Class<T> type) {
		final Object value = properties.get( name );
		if ( value == null ) {
			return null;
		}
		if ( type == String.class && !( value instanceof String ) ) {
			return (T) value.toString();
		}
		return type.cast( value );
	}

	public <T> T getProperty(String name, Class<T> type, T defaultValue) {
		final T value = getProperty( name, type );
		return value != null ? value : defaultValue;
	}
}
