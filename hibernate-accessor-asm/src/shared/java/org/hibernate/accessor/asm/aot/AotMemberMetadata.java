package org.hibernate.accessor.asm.aot;

import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;

import java.util.Comparator;

/**
 * Metadata for a field or getter/setter method.
 * For methods, {@link #isPrimitive()} describes the getter's return value or the setter's parameter.
 * The hierarchy is sealed: a member is either a field or a method, and emission dispatches on that
 * distinction, so callers cannot introduce a third kind the generator would not know how to emit.
 */
public sealed interface AotMemberMetadata extends Comparable<AotMemberMetadata>
		permits AotFieldMetadata, AotMethodMetadata {
	Comparator<AotMemberMetadata> COMPARATOR = Comparator.comparing( AotMemberMetadata::declaringClass, nullsFirst( naturalOrder() ) )
			.thenComparing( AotMemberMetadata::name, nullsFirst( naturalOrder() ) )
			.thenComparing( AotMemberMetadata::descriptor, nullsFirst( naturalOrder() ) );

	/**
	 * Returns the field or method name.
	 */
	String name();

	/**
	 * Returns the JVM field or method descriptor.
	 */
	String descriptor();

	/**
	 * Returns the key in this member's lookup namespace: a field name, or a method name followed by
	 * its full JVM descriptor. Field and method lookup namespaces are separate.
	 */
	String lookupKey();

	/**
	 * Returns whether the value being read or written is primitive.
	 */
	boolean isPrimitive();

	/**
	 * Returns the binary name of the class or interface declaring the member.
	 */
	String declaringClass();

	@Override
	default int compareTo(AotMemberMetadata o) {
		return AotMemberMetadata.COMPARATOR.compare( this, o );
	}
}
