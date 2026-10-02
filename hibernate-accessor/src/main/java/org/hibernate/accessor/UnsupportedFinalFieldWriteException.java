package org.hibernate.accessor;

/**
 * Thrown when a factory is asked to create a {@link ValueWriter} for a final field
 * but that factory does not support writing to final fields. Consumers should catch
 * this to fall back to another factory, such as {@link AccessorFactory#reflection()}
 * or handle the exception as appropriate.
 */
public class UnsupportedFinalFieldWriteException extends AccessorException {

	public UnsupportedFinalFieldWriteException(String message) {
		super( message );
	}

	public UnsupportedFinalFieldWriteException(String message, Throwable cause) {
		super( message, cause );
	}
}
