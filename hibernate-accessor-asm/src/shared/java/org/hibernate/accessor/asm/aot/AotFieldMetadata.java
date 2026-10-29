package org.hibernate.accessor.asm.aot;

/**
 * Metadata for an instance field. Reader and writer collections determine the requested access.
 *
 * @param descriptor JVM field descriptor
 * @param declaringClass binary name of the declaring class
 */
public record AotFieldMetadata( String name, String descriptor, boolean isPrimitive,
								String declaringClass)
		implements AotMemberMetadata {
	@Override
	public String lookupKey() {
		return name();
	}
}
