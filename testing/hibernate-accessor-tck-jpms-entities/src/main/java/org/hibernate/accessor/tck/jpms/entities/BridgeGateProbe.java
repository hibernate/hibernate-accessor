package org.hibernate.accessor.tck.jpms.entities;

import java.lang.invoke.MethodHandles;

/**
 * Test fixture living inside the <em>target</em> module.
 * <p>
 * Verifies that the bridge class ({@code $$HibernateAccessorBridge}) is injected into
 * this package by the accessor infrastructure, and exposes a full-privilege lookup for
 * this module so that tests in the test module can exercise the entry-point authorisation
 * checks on {@link org.hibernate.accessor.spi.CrossClassLoaderLookupBridge}.
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

	/**
	 * Returns a full-privilege lookup for this (entity) module. Used by tests in the
	 * test module to exercise entry-point authorisation on
	 * {@link org.hibernate.accessor.spi.CrossClassLoaderLookupBridge} with a lookup
	 * from a foreign module.
	 */
	public static MethodHandles.Lookup entityModuleLookup() {
		return MethodHandles.lookup();
	}
}
