package org.hibernate.accessor;

/**
 * Creates new instances of a type by invoking a constructor.
 *
 * <p>Obtain an instance via {@link AccessorFactory#instantiator(java.lang.reflect.Constructor)}.
 *
 * @param <T> the type this instantiator creates
 */
public interface Instantiator<T> {

	/**
	 * Creates a new instance by invoking the underlying constructor with the given arguments.
	 *
	 * <p>Any exception thrown by the constructor body -- whether an {@link Error},
	 * a {@link RuntimeException} or a checked exception -- propagates unchanged, as the exact
	 * throwable the constructor raised. This behaviour is identical across all accessor strategies.
	 * Note that a checked exception may therefore be thrown even though it is not declared here.
	 *
	 * @param args the constructor arguments
	 * @return a new instance of {@code T}
	 * @throws AccessorException if the accessor infrastructure itself fails (as opposed to
	 * the constructor body throwing, which propagates unchanged)
	 */
	T create(Object... args);
}
