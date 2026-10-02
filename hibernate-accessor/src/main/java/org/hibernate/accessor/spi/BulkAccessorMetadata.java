package org.hibernate.accessor.spi;

import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import org.hibernate.accessor.AccessorException;

/** Immutable index metadata owned by a generated bulk accessor instance. */
public record BulkAccessorMetadata( Map<String, Integer> fieldIndices,
									Map<String, Integer> getterMethodIndices,
									Map<String, Integer> setterMethodIndices,
									Map<String, Integer> constructorIndices) {

	public BulkAccessorMetadata {
		fieldIndices = Map.copyOf( fieldIndices );
		getterMethodIndices = Map.copyOf( getterMethodIndices );
		setterMethodIndices = Map.copyOf( setterMethodIndices );
		constructorIndices = Map.copyOf( constructorIndices );
	}

	public int fieldIndex(Field field) {
		Integer index = fieldIndices.get( field.getName() );
		if ( index == null ) {
			throw new AccessorException( "Unknown field: " + field );
		}
		return index;
	}

	public int methodIndex(Method method) {
		String key = methodKey( method );
		if ( method.getParameterCount() == 0 && method.getReturnType() != void.class ) {
			Integer index = getterMethodIndices.get( key );
			if ( index != null ) {
				return index;
			}
		}
		else if ( method.getParameterCount() == 1 ) {
			Integer index = setterMethodIndices.get( key );
			if ( index != null ) {
				return index;
			}
		}
		throw new AccessorException( "Unknown method: " + method );
	}

	public int constructorIndex(Constructor<?> constructor) {
		Integer index = constructorIndices.get( constructorDescriptor( constructor ) );
		if ( index == null ) {
			throw new AccessorException( "Unknown constructor: " + constructor );
		}
		return index;
	}

	private static String methodKey(Method method) {
		return method.getName() + MethodType.methodType( method.getReturnType(), method.getParameterTypes() ).descriptorString();
	}

	private static String constructorDescriptor(Constructor<?> constructor) {
		return MethodType.methodType( void.class, constructor.getParameterTypes() ).descriptorString();
	}
}
