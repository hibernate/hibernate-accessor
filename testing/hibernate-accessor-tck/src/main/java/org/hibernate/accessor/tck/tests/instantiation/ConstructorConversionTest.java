package org.hibernate.accessor.tck.tests.instantiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hibernate.accessor.tck.util.TckHelper;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class ConstructorConversionTest {
	public static class Bean {
		public final Object value;

		public Bean(boolean value) {
			this.value = value;
		}

		public Bean(byte value) {
			this.value = value;
		}

		public Bean(short value) {
			this.value = value;
		}

		public Bean(char value) {
			this.value = value;
		}

		public Bean(int value) {
			this.value = value;
		}

		public Bean(long value) {
			this.value = value;
		}

		public Bean(float value) {
			this.value = value;
		}

		public Bean(double value) {
			this.value = value;
		}

	}

	@ParameterizedTest
	@ValueSource(classes = { boolean.class, byte.class, short.class, char.class, int.class, long.class, float.class, double.class })
	void constructorArgumentsMatchReflection(Class<?> parameterType) throws Exception {
		var constructor = Bean.class.getConstructor( parameterType );
		var instantiator = TckHelper.factory().instantiator( constructor );
		Object[] sources = { true, (byte) 7, (short) 7, 'A', 7, 7L, 7.5f, 7.5, null };
		for ( Object source : sources ) {
			Bean expected;
			try {
				expected = constructor.newInstance( source );
			}
			catch (IllegalArgumentException e) {
				assertThrows( RuntimeException.class, () -> instantiator.create( source ), parameterType + " <- " + source );
				continue;
			}
			assertEquals( expected.value, instantiator.create( source ).value, parameterType + " <- " + source );
		}
		assertThrows( RuntimeException.class, instantiator::create );
	}
}
