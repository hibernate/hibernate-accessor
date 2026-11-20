package org.hibernate.accessor.methodhandle.impl;

import java.lang.invoke.MethodHandle;

import org.hibernate.accessor.Instantiator;
import org.hibernate.accessor.impl.AccessorThrowables;
import org.hibernate.accessor.spi.MemberValidation;

public class MethodHandleInstantiator<T> implements Instantiator<T> {

	private final MethodHandle target;
	private final int parameterCount;

	public MethodHandleInstantiator(MethodHandle target, int parameterCount) {
		this.target = target;
		this.parameterCount = parameterCount;
	}

	@Override
	@SuppressWarnings("unchecked")
	public T create(Object... args) {
		MemberValidation.validateConstructorArity( args, parameterCount );
		try {
			return (T) target.invoke( args );
		}
		catch (Throwable t) {
			throw AccessorThrowables.sneakyThrow( t );
		}
	}
}
