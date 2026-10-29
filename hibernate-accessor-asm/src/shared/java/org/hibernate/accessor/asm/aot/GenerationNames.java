package org.hibernate.accessor.asm.aot;

import java.util.Objects;

/** Binary names of generated classes and the caller's runtime creation hooks.
 * The hooks class must expose public static createReader, createWriter and createInstantiator
 * methods with signature {@code Object (int classIndex, int memberIndex)}. It must be visible
 * to transformed hosts, including hosts in parent classloaders.
 */
public record GenerationNames(String factory, String reader, String writer, String instantiator, String runtimeHooks) {
	public GenerationNames {
		Objects.requireNonNull( factory );
		Objects.requireNonNull( reader );
		Objects.requireNonNull( writer );
		Objects.requireNonNull( instantiator );
		Objects.requireNonNull( runtimeHooks );
	}
}
