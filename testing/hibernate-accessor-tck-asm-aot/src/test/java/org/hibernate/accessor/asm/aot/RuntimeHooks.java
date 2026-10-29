package org.hibernate.accessor.asm.aot;

/** Test framework-owned runtime bridge; intentionally has no generator dependency. */
public final class RuntimeHooks {
	public static ClassLoader loader;

	public static Object createReader(int host, int member) {
		return create( "Reader", host, member );
	}

	public static Object createWriter(int host, int member) {
		return create( "Writer", host, member );
	}

	public static Object createInstantiator(int host, int member) {
		return create( "Instantiator", host, member );
	}

	private static Object create(String role, int host, int member) {
		try {
			return loader.loadClass( "generated." + role ).getConstructor( int.class, int.class ).newInstance( host, member );
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException( e );
		}
	}
}
