package org.hibernate.accessor.asm.aot;

/**
 * Generation-time switch sizes, independent for each operation and dispatch level.
 * Member constructor size is a conversion budget: effective cases per helper are
 * max(1, memberConstructor / max(1, largest constructor arity)). String sizes bound
 * each lookup helper, including helpers for colliding hashes. Positive sizes are
 * required; large values can still exceed JVM method/class limits for a given model.
 */
public record AotChunkSizes(int typeRead, int typeWrite, int typeConstructor,
							int memberRead, int memberWrite, int memberConstructor, int typeLookup, int memberLookup) {
	public static final AotChunkSizes DEFAULT = new AotChunkSizes( 256, 256, 256, 256, 256, 256, 500, 500 );

	public AotChunkSizes {
		positive( "typeRead", typeRead );
		positive( "typeWrite", typeWrite );
		positive( "typeConstructor", typeConstructor );
		positive( "memberRead", memberRead );
		positive( "memberWrite", memberWrite );
		positive( "memberConstructor", memberConstructor );
		positive( "typeLookup", typeLookup );
		positive( "memberLookup", memberLookup );
	}

	private static void positive(String name, int value) {
		if ( value <= 0 ) {
			throw new IllegalArgumentException( name + " chunk size must be positive" );
		}
	}
}
