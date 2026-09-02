/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.tck.jpms;

import java.lang.invoke.MethodHandles;

import org.hibernate.accessor.asm.HibernateAccessorAsmFactory;
import org.hibernate.accessor.bytebuddy.HibernateAccessorByteBuddyFactory;
import org.hibernate.accessor.tck.jpms.entities.BridgeGateProbe;
import org.hibernate.accessor.tck.jpms.entities.SimpleEntity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for the authorisation checks on the cross-module bridge method
 * ({@code $$HibernateAccessorBridge.$$defineAccessor}) injected into the entity module.
 * <p>
 * The gate is exercised from inside the entity module via {@link BridgeGateProbe}: that is
 * the only vantage point from which the package-private bridge method is even reachable.
 * The first check rejects lookups lacking full-privilege access; the second compares the
 * caller's module <em>name</em> against the accessor SPI module's name, which rejects
 * callers in any other module. See the probe's javadoc for why the fixture lives in the
 * entity module rather than here.
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
			HibernateAccessorAsmFactory.factory( lookup )
					.valueReader( SimpleEntity.class.getDeclaredMethod( "getName" ) );
			HibernateAccessorByteBuddyFactory.factory( lookup )
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
		ensureBridgeInjected();

		// A caller inside the target module holds a genuine full-privilege lookup for that
		// module, and can reach the package-private bridge method — but its module's name
		// is not the accessor SPI module's name, so the module-name check must reject it.
		Throwable rejection = BridgeGateProbe.invokeWithForeignFullPrivilegeLookup();
		assertThat( rejection )
				.as( "a full-privilege lookup from the wrong module must be rejected" )
				.isInstanceOf( IllegalAccessError.class )
				.hasMessageContaining( "unauthorised module" );
	}

	@Test
	void gateRejectsNonFullPrivilegeLookup() {
		ensureBridgeInjected();

		// A lookup lacking full-privilege access must be rejected before any bytecode is touched.
		Throwable rejection = BridgeGateProbe.invokeWithoutFullPrivilege();
		assertThat( rejection )
				.as( "a lookup without full-privilege access must be rejected" )
				.isInstanceOf( IllegalAccessError.class )
				.hasMessageContaining( "full-privilege" );
	}
}
