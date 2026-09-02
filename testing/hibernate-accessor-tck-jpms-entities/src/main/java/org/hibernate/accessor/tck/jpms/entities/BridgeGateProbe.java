/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.tck.jpms.entities;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Test fixture living inside the <em>target</em> module.
 * <p>
 * Once the accessor infrastructure has injected its bridge class
 * ({@code $$HibernateAccessorBridge}) into this package, this probe attempts to invoke
 * that class's package-private {@code $$defineAccessor} method from within the target
 * module. This exercises the bridge's authorisation gate: even a caller that <em>can</em>
 * reach the method (because it is in the same package/module as the injected class) must
 * still present a full-privilege lookup belonging to the accessor SPI module.
 * <p>
 * This can only be tested from here — a foreign module cannot reference the package-private
 * method at all, so the "reachable but unauthorised" case is only observable from the
 * target module itself.
 */
public final class BridgeGateProbe {

	public static final String BRIDGE_CLASS =
			"org.hibernate.accessor.tck.jpms.entities.$$HibernateAccessorBridge";
	public static final String BRIDGE_METHOD = "$$defineAccessor";

	/**
	 * Fully-qualified names of the bridge classes that may be injected into the accessor
	 * modules' SPI packages (the generated bulk accessors implement interfaces from those
	 * packages, so a bridge can be defined there in addition to the one in this package).
	 * The gate tests must target the bridge in <em>this</em> package explicitly, since
	 * {@code Class.forName} on the bridge simple name resolves nondeterministically between
	 * packages when several accessor jars are on the module path.
	 */
	public static final String[] POSSIBLE_BRIDGE_CLASSES = {
			"org.hibernate.accessor.tck.jpms.entities.$$HibernateAccessorBridge",
			"org.hibernate.accessor.asm.spi.$$HibernateAccessorBridge",
			"org.hibernate.accessor.bytebuddy.spi.$$HibernateAccessorBridge",
	};

	private BridgeGateProbe() {
	}

	/**
	 * @return {@code true} if any injected bridge class is present in a readable module.
	 */
	public static boolean bridgeInjected() {
		try {
			for (String name : POSSIBLE_BRIDGE_CLASSES) {
				Class.forName( name, false, BridgeGateProbe.class.getClassLoader() );
				return true;
			}
			return false;
		}
		catch (ClassNotFoundException e) {
			return false;
		}
	}

	static MethodHandle bridgeMethod() throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException {
		// Only the bridge in this package is reachable from a lookup in this package; the
		// bridges injected into the accessor SPI packages are package-private there and
		// invisible to us, so resolution of BRIDGE_CLASS must land on this one.
		final Class<?> bridge = Class.forName( BRIDGE_CLASS, true, BridgeGateProbe.class.getClassLoader() );
		// Same package: this module's own lookup has PACKAGE access to the bridge method.
		return MethodHandles.lookup().findStatic( bridge, BRIDGE_METHOD,
				MethodType.methodType( Object.class, MethodHandles.Lookup.class, Class.class, byte[].class ) );
	}

	/**
	 * Invoke the bridge with an authentic full-privilege lookup that belongs to <em>this</em>
	 * (target) module rather than the accessor SPI module. Must be rejected by the
	 * module-identity check.
	 *
	 * @return the throwable raised by the bridge, or {@code null} if the call unexpectedly succeeded
	 */
	public static Throwable invokeWithForeignFullPrivilegeLookup() {
		try {
			bridgeMethod().invoke( MethodHandles.lookup(), SimpleEntity.class, new byte[0] );
			return null;
		}
		catch (Throwable t) {
			return t;
		}
	}

	/**
	 * Invoke the bridge with a lookup lacking full-privilege access. Must be rejected by the
	 * {@code hasFullPrivilegeAccess} check.
	 * <p>
	 * The lookup class is this class, not {@code Object}, because the bridge's second
	 * guard compares the caller module's <em>name</em> against the SPI module's name; a
	 * lookup on a foreign class (e.g. {@link Object}) would be rejected by that name
	 * check instead of the one under test.
	 *
	 * @return the throwable raised by the bridge, or {@code null} if the call unexpectedly succeeded
	 */
	public static Throwable invokeWithoutFullPrivilege() {
		try {
			bridgeMethod().invoke( MethodHandles.publicLookup().in( BridgeGateProbe.class ), SimpleEntity.class, new byte[0] );
			return null;
		}
		catch (Throwable t) {
			return t;
		}
	}
}
