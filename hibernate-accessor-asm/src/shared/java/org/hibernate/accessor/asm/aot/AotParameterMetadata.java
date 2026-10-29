package org.hibernate.accessor.asm.aot;

/**
 * Metadata for a constructor parameter, using a JVM type descriptor.
 */
public record AotParameterMetadata(String name, String descriptor, boolean isPrimitive) {
}
