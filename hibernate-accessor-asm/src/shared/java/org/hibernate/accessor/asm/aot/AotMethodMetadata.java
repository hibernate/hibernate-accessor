package org.hibernate.accessor.asm.aot;

/**
 * Metadata for an instance getter or setter.
 *
 * @param descriptor JVM method descriptor, including parameter and return types
 * @param isPrimitive whether the getter's return value or the setter's parameter is primitive
 * @param declaringClass binary name of the declaring class or interface
 * @param isInterface whether invocation must use {@code invokeinterface}
 * @param returnDescriptor JVM descriptor of the return type, including {@code V} for void
 */
public record AotMethodMetadata(String name, String descriptor, boolean isPrimitive,
								String declaringClass, boolean isInterface,
								String returnDescriptor)
		implements AotMemberMetadata {
	@Override
	public String lookupKey() {
		return name() + descriptor();
	}
}
