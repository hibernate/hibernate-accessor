/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.spi;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class AccessorConfiguration {

	public static final AccessorConfiguration EMPTY = new AccessorConfiguration( MethodHandles.lookup() );

	public static final String ACCESS_CONTEXT = "hibernate.accessor.access.context";
	public static final String LOOKUP = "hibernate.accessor.lookup";
	public static final String DUMP_BYTECODE_DIR = "hibernate.accessor.bytecode.dump.dir";

	private final Map<String, Object> properties;
	private final AccessContext accessContext;

	public AccessorConfiguration(MethodHandles.Lookup lookup) {
		this( lookup, Map.of() );
	}

	public AccessorConfiguration(MethodHandles.Lookup lookup, Map<String, Object> properties) {
		this( new LookupAccess( lookup ), properties );
	}

	public AccessorConfiguration(AccessContext accessContext, Map<String, Object> properties) {
		final Map<String, Object> merged = new HashMap<>( properties );
		merged.put( LOOKUP, accessContext.lookup() );
		merged.put( ACCESS_CONTEXT, Objects.requireNonNull( accessContext, "context" ) );
		this.properties = merged;
		this.accessContext = accessContext;
	}

	public MethodHandles.Lookup lookup() {
		return accessContext.lookup();
	}

	/**
	 * @return the supplied context, or a lookup-based JVM implementation
	 */
	public AccessContext accessContext() {
		return accessContext;
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
