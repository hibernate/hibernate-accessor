package org.hibernate.accessor.asm.aot;

import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;

import java.util.Comparator;
import java.util.List;

/**
 * Metadata for a constructor.
 *
 * @param declaringClass binary name of the class to instantiate
 * @param host binary name of the type hosting the injected instantiation method
 * @param descriptor JVM constructor descriptor, with a void return type
 * @param parameters parameter metadata in declaration order
 */
public record AotConstructorMetadata(   String declaringClass, String host, String descriptor,
										List<AotParameterMetadata> parameters)
		implements Comparable<AotConstructorMetadata> {

	public AotConstructorMetadata {
		parameters = List.copyOf( parameters );
	}

	private static final Comparator<AotConstructorMetadata> COMPARATOR = Comparator
			.comparing( AotConstructorMetadata::declaringClass, nullsFirst( naturalOrder() ) )
			.thenComparing( AotConstructorMetadata::descriptor, nullsFirst( naturalOrder() ) );

	@Override
	public int compareTo(AotConstructorMetadata o) {
		return COMPARATOR.compare( this, o );
	}
}
