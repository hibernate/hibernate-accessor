package org.hibernate.accessor.methodhandle.impl;

import java.lang.invoke.MethodHandle;

import org.hibernate.accessor.Instantiator;
import org.hibernate.accessor.impl.AccessorThrowables;

public class MethodHandleInstantiator<T> implements Instantiator<T> {

	private final MethodHandle target;

	public MethodHandleInstantiator(MethodHandle target) {
		this.target = target;
	}

	@Override
	@SuppressWarnings("unchecked")
	public T create(Object... args) {
		try {
			return (T) target.invoke( args );
		}
		catch (Throwable t) {
			throw AccessorThrowables.sneakyThrow( t );
		}
	}
}
