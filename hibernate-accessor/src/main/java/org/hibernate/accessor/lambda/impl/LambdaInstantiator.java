package org.hibernate.accessor.lambda.impl;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;

import org.hibernate.accessor.Instantiator;
import org.hibernate.accessor.impl.AccessorThrowables;
import org.hibernate.accessor.logging.impl.CoreLog;
import org.hibernate.accessor.spi.MemberValidation;

public class LambdaInstantiator<T> implements Instantiator<T> {
	private final MethodHandle handle;
	private final int parameterCount;

	public LambdaInstantiator(MethodHandles.Lookup lookup, Constructor<T> constructor) {
		this.parameterCount = constructor.getParameterCount();
		try {
			this.handle = lookup.unreflectConstructor( constructor ).asFixedArity()
					.asSpreader( Object[].class, parameterCount );
		}
		catch (IllegalAccessException e) {
			throw CoreLog.INSTANCE.errorCreatingHandle( constructor, e, e.getMessage() );
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public T create(Object... args) {
		MemberValidation.validateConstructorArity( args, parameterCount );
		try {
			return (T) handle.invoke( args );
		}
		catch (Throwable t) {
			throw AccessorThrowables.sneakyThrow( t );
		}
	}
}
