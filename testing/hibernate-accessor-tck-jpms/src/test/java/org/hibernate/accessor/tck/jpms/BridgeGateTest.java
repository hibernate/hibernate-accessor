package org.hibernate.accessor.tck.jpms;

import java.lang.invoke.MethodHandles;

import org.hibernate.accessor.asm.AsmAccessorFactory;
import org.hibernate.accessor.bytebuddy.ByteBuddyAccessorFactory;
import org.hibernate.accessor.spi.CrossClassLoaderLookupBridge;
import org.hibernate.accessor.tck.jpms.entities.BridgeGateProbe;
import org.hibernate.accessor.tck.jpms.entities.SimpleEntity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests for the entry-point authorisation checks on
 * {@link CrossClassLoaderLookupBridge}.
 * <p>
 * The bridge verifies that the caller-supplied lookup belongs to the same module that
 * created the bridge and has full-privilege access. These tests exercise both the
 * foreign-module and non-full-privilege rejection paths.
 */
class BridgeGateTest {

	/**
	 * Creating any accessor for an entity in a foreign module injects the bridge class into
	 * that module. Do this via both bytecode factories so the bridge is present regardless of
	 * which one wins the race to define it.
	 */
	private static void ensureBridgeInjected() {
		MethodHandles.Lookup lookup = MethodHandles.lookup();
		try {
			AsmAccessorFactory.factory( lookup )
					.valueReader( SimpleEntity.class.getDeclaredMethod( "getName" ) );
			ByteBuddyAccessorFactory.factory( lookup )
					.valueReader( SimpleEntity.class.getDeclaredMethod( "getName" ) );
		}
		catch (NoSuchMethodException e) {
			throw new AssertionError( e );
		}
	}

	@Test
	void bridgeIsInjectedIntoForeignModule() {
		ensureBridgeInjected();
		assertThat( BridgeGateProbe.bridgeInjected() )
				.as( "the bridge class must have been injected into the entity module" )
				.isTrue();
	}

	@Test
	void gateRejectsForeignFullPrivilegeLookup() {
		// Create a bridge owned by the test module
		CrossClassLoaderLookupBridge bridge = new CrossClassLoaderLookupBridge(
				MethodHandles.lookup(), name -> new byte[0] );

		// A full-privilege lookup from the entity module must be rejected: different module
		MethodHandles.Lookup foreignLookup = BridgeGateProbe.entityModuleLookup();
		assertThatThrownBy( () -> bridge.defineAccessor( foreignLookup, SimpleEntity.class, new byte[0] ) )
				.as( "a full-privilege lookup from a different module must be rejected" )
				.isInstanceOf( IllegalAccessError.class )
				.hasMessageContaining( "different module" );
	}

	@Test
	void gateRejectsNonFullPrivilegeLookup() {
		// Create a bridge owned by the test module
		CrossClassLoaderLookupBridge bridge = new CrossClassLoaderLookupBridge(
				MethodHandles.lookup(), name -> new byte[0] );

		// A lookup from the same module but lacking full-privilege access must be rejected
		MethodHandles.Lookup downgraded = MethodHandles.publicLookup().in( BridgeGateTest.class );
		assertThatThrownBy( () -> bridge.defineAccessor( downgraded, SimpleEntity.class, new byte[0] ) )
				.as( "a lookup without full-privilege access must be rejected" )
				.isInstanceOf( IllegalAccessError.class )
				.hasMessageContaining( "full-privilege" );
	}
}
