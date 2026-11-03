package org.hibernate.accessor.asm.aot;

import java.util.Objects;

/** Binary names of the generated factory and three shared implementations. */
public record GenerationNames(String factory, String reader, String writer, String instantiator) {
	public GenerationNames {
		Objects.requireNonNull( factory );
		Objects.requireNonNull( reader );
		Objects.requireNonNull( writer );
		Objects.requireNonNull( instantiator );
	}
}
