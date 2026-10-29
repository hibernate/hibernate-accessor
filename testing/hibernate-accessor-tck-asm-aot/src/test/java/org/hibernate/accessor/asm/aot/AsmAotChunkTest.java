package org.hibernate.accessor.asm.aot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.stream.Stream;

import org.hibernate.accessor.Instantiator;
import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.testing.aot.asm.AsmDispatchModel;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class AsmAotChunkTest {
	static Stream<AotChunkSizes> sizes() {
		return Stream.of( AotChunkSizes.DEFAULT,
				new AotChunkSizes( 1, 1, 1, 1, 1, 1, 1, 1 ),
				new AotChunkSizes( 2, 3, 4, 3, 4, 8, 2, 3 ),
				new AotChunkSizes( 7, 7, 7, 7, 7, 42, 7, 7 ),
				new AotChunkSizes( Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
						Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE ) );
	}

	@ParameterizedTest
	@MethodSource("sizes")
	void dispatchAndLookupAcrossBoundaries(AotChunkSizes sizes) throws Exception {
		for ( boolean collisions : new boolean[] { false, true } ) {
			// Setup verifies every field read/write and constructor, including partial last chunks.
			var model = new AsmDispatchModel( 7, 7, 7, sizes, collisions );
			Class<?> host = model.targets[0].getClass();
			assertHelpers( host, "$$__hibernateAccessor_read", 7, sizes.memberRead() );
			assertHelpers( host, "$$__hibernateAccessor_write", 7, sizes.memberWrite() );
			assertHelpers( host, "$$__hibernateAccessor_create", 7, Math.max( 1, sizes.memberConstructor() / 6 ) );
			assertHelpers( model.readers[0].getClass(), "get", 7, sizes.typeRead() );
			assertHelpers( model.writers[0].getClass(), "set", 7, sizes.typeWrite() );
			assertHelpers( model.constructors[0].getClass(), "create", 7, sizes.typeConstructor() );
			assertThat( Arrays.stream( host.getDeclaredMethods() ).anyMatch( m -> m.getName().startsWith( "$$__hibernateAccessor_fieldReader$" ) ) )
					.isEqualTo( 7 > sizes.memberLookup() );
			assertThat( Arrays.stream( model.factory.getClass().getDeclaredMethods() ).anyMatch( m -> m.getName().startsWith( "valueReader_$$__hibernateAccessor_fieldReader$" ) ) )
					.isEqualTo( 7 > sizes.typeLookup() );
			assertThat( host.getMethod( "$$__hibernateAccessor_fieldReader", String.class ).invoke( null, "missing" ) ).isNull();
			for ( int invalid : new int[] { -1, 7, Integer.MAX_VALUE } ) {
				ValueReader<?> reader = (ValueReader<?>) model.readers[0].getClass().getConstructor( int.class, int.class )
						.newInstance( invalid, 0 );
				assertThatThrownBy( () -> reader.get( model.targets[0] ) ).isInstanceOf( IllegalArgumentException.class );
				ValueReader<?> member = (ValueReader<?>) model.readers[0].getClass().getConstructor( int.class, int.class )
						.newInstance( 0, invalid );
				assertThatThrownBy( () -> member.get( model.targets[0] ) ).isInstanceOf( IllegalArgumentException.class );
				for ( int[] indexes : new int[][] { { invalid, 0 }, { 0, invalid } } ) {
					ValueWriter writer = (ValueWriter) model.writers[0].getClass().getConstructor( int.class, int.class )
							.newInstance( indexes[0], indexes[1] );
					assertThatThrownBy( () -> writer.set( model.targets[0], "value" ) ).isInstanceOf( IllegalArgumentException.class );
					Instantiator<?> ctor = (Instantiator<?>) model.constructors[0].getClass().getConstructor( int.class, int.class )
							.newInstance( indexes[0], indexes[1] );
					assertThatThrownBy( () -> ctor.create() ).isInstanceOf( IllegalArgumentException.class );
				}
			}
		}
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, -1, Integer.MIN_VALUE })
	void rejectsInvalidSizesForEveryAxis(int invalid) {
		for ( int axis = 0; axis < 8; axis++ ) {
			int[] sizes = { 1, 1, 1, 1, 1, 1, 1, 1 };
			sizes[axis] = invalid;
			assertThatThrownBy( () -> new AotChunkSizes( sizes[0], sizes[1], sizes[2], sizes[3], sizes[4], sizes[5], sizes[6], sizes[7] ) )
					.isInstanceOf( IllegalArgumentException.class ).hasMessageContaining( "positive" );
		}
	}

	private static void assertHelpers(Class<?> type, String method, int count, int size) {
		long helpers = Arrays.stream( type.getDeclaredMethods() ).filter( m -> m.getName().startsWith( method + "$" ) ).count();
		assertThat( helpers ).as( type.getName() + "." + method ).isEqualTo( count <= size ? 0 : ( count - 1 ) / size + 1 );
	}
}
