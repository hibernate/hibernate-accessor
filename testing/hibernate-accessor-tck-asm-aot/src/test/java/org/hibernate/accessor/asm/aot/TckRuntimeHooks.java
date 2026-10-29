package org.hibernate.accessor.asm.aot;

/** Runtime hooks for the prepared TCK, with no generator dependency or mutable state. */
public final class TckRuntimeHooks {
	public static Object createReader(int host, int member) {
		return new tckgenerated.Reader( host, member );
	}

	public static Object createWriter(int host, int member) {
		return new tckgenerated.Writer( host, member );
	}

	public static Object createInstantiator(int host, int member) {
		return new tckgenerated.Instantiator( host, member );
	}
}
