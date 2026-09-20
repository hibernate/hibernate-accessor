/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.spi;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.function.Function;

import org.hibernate.accessor.AccessorException;

/**
 * Defines a generated accessor class as a nestmate of a target class and returns a
 * freshly constructed instance of it, even when the target class is in a different
 * {@link ClassLoader} or {@link Module} than the caller.
 * <p>
 * <b>Why this is needed:</b>
 * <p>
 * On Java 9+, {@link MethodHandles#privateLookupIn(Class, Lookup)} drops the
 * {@code MODULE} access bit from the returned lookup whenever the lookup crosses a
 * module boundary -- whether between two unnamed modules (different classloaders) or
 * between named JPMS modules (same classloader). See
 * <a href="https://bugs.openjdk.org/browse/JDK-8228624">JDK-8228624</a>:
 * <em>"Teleporting across modules will always record the original lookup class as
 * the previous lookup class and drops MODULE access."</em>
 * <p>
 * {@link Lookup#defineHiddenClass(byte[], boolean, Lookup.ClassOption...)} requires
 * "full privilege access" (both {@code PRIVATE} and {@code MODULE} bits set), so it
 * fails with {@code IllegalAccessException} when given a lookup that crossed a module
 * boundary.
 * <p>
 * This is not a JDK bug; it is by design. The cross-module lookup retains
 * {@code PRIVATE + PACKAGE} access (sufficient for {@code unreflect*} and
 * {@link Lookup#defineClass(byte[])}), but not {@code MODULE} (required for
 * {@code defineHiddenClass}).
 * <p>
 * <b>How the bridge works:</b>
 * <ol>
 *   <li>Detect whether the target class is in the same classloader <em>and</em> the
 *       same module as the caller. If so, define the hidden class directly with the
 *       caller lookup (zero overhead) and instantiate it.</li>
 *   <li>Otherwise, obtain a cross-boundary lookup via {@code privateLookupIn}
 *       (retains {@code PACKAGE} access; requires the target package to be
 *       {@code opens}-ed to the caller's module).</li>
 *   <li>Use {@link Lookup#defineClass(byte[])} (which only requires {@code PACKAGE}
 *       access) to inject a small bridge class into the target module. The bridge is
 *       placed at a predictable location: the target's own runtime package, under the
 *       fixed name {@value #BRIDGE_CLASS_SIMPLE_NAME}, so it can be rediscovered later
 *       with {@link Lookup#findClass(String)} -- no lookup tables are needed. The bridge
 *       class exposes a single <em>package-private</em>
 *       method {@value #BRIDGE_METHOD_NAME} that calls {@link MethodHandles#lookup()}
 *       (caller-sensitive, so full-privilege for the target module), defines the
 *       supplied bytecode as a hidden nestmate of the target class, and returns the
 *       defined class. The bridge also bakes its own {@link MethodHandle} to that method
 *       into a {@code static final} field ({@value #BRIDGE_HANDLE_FIELD_NAME}) in its
 *       class initializer, so users of the bridge read one shared handle instead of each
 *       resolving the method again.</li>
 *   <li>Callers of this bridge must present a full-privilege {@link Lookup} belonging
 *       to the same module that created the bridge. This is verified at the entry
 *       point ({@link #defineAccessor}) before any bytecode is processed. A caller
 *       that passes this check necessarily has the
 *       capability to perform the same operation themselves (they could inject their
 *       own bridge via {@code defineClass}), so the bridge is a convenience, not a
 *       privilege escalation.</li>
 * </ol>
 * <p>
 * <b>Least authority:</b> a raw full-privilege {@link Lookup} for the target module is
 * never returned to, cached by, or otherwise escapes this bridge. The full-privilege
 * lookup exists only as a local variable inside the injected method for the duration of
 * one {@code defineHiddenClass} call. Callers receive either the defined accessor class
 * or a constructed instance. The memoization holds a {@link MethodHandle} to the injected
 * method -- a single bounded operation, not a reusable capability -- in a
 * {@link ClassValue} keyed by the target class (all entries for classes in the same
 * package share the bridge's pre-computed handle): entries are cleared automatically when
 * the target class is unloaded, so nothing here can keep a foreign classloader alive.
 * <p>
 * <b>Supported posture:</b> a qualified {@code opens P to <caller module>} (for example
 * {@code opens com.acme.entities to org.hibernate.orm}). Under that posture the injected
 * method is unreachable from any other module. On the classpath, target and caller share
 * the unnamed module, so the same-classloader fast path applies and no class is injected.
 * <p>
 * This affects:
 * <ul>
 *   <li>App servers (WildFly, WebLogic, Tomcat), OSGi containers, and bytecode
 *       enhancement classloaders -- any scenario where entity classes are loaded by a
 *       different classloader than Hibernate.</li>
 *   <li>JPMS modular applications -- where entity classes live in a different named
 *       module than Hibernate (e.g. {@code my.entities} vs {@code org.hibernate.orm}).</li>
 * </ul>
 *
 * @see <a href="https://bugs.openjdk.org/browse/JDK-8228624">JDK-8228624</a>
 * @see <a href="https://bugs.openjdk.org/browse/JDK-8233726">JDK-8233726</a>
 */
public final class CrossClassLoaderLookupBridge {

	/**
	 * Simple name of the bridge class injected into foreign modules. The bridge is always
	 * placed in the target's own runtime package under this fixed name, making its
	 * location predictable: it can be rediscovered via {@link Lookup#findClass(String)}
	 * without any lookup tables. The access check performed by {@code findClass} also
	 * rejects a same-named class coming from an ancestor classloader, since that class is
	 * in a different runtime package.
	 */
	public static final String BRIDGE_CLASS_SIMPLE_NAME = "$$HibernateAccessorBridge";

	/**
	 * Name of the package-private static method on the bridge class that defines the
	 * generated accessor as a hidden nestmate of the target class and returns the
	 * defined class. Its signature is
	 * {@code static Object <name>(MethodHandles.Lookup proof, Class<?> target, byte[] bytecode)}.
	 * The method verifies that the supplied {@code proof} can access the bridge class's
	 * package via {@link MethodHandles#privateLookupIn}: if the caller could reach the
	 * package, they could already inject their own bridge, so this bridge is not a
	 * privilege escalation.
	 */
	public static final String BRIDGE_METHOD_NAME = "$$defineAccessor";

	/**
	 * Name of the {@code static final MethodHandle} field on the bridge class, initialized once
	 * in the bridge's own class initializer with a handle to {@value #BRIDGE_METHOD_NAME}. The
	 * bridge resolves this handle with its own full-privilege lookup, so every target class in
	 * the package can share that single instance rather than resolving the method per class.
	 */
	public static final String BRIDGE_HANDLE_FIELD_NAME = "DEFINE_ACCESSOR_MH";

	private final ClassValue<MethodHandle> bridgeHandles = new ClassValue<>() {
		@Override
		protected MethodHandle computeValue(Class<?> type) {
			return createBridgeHandle( type );
		}
	};
	private final Lookup callerLookup;
	private final Function<String, byte[]> bridgeBytecodeGenerator;

	/**
	 * @param callerLookup the lookup from the accessor factory's own context. Must have
	 *        full privilege access in its own module (as obtained from
	 *        {@link MethodHandles#lookup()}); it is used both to bootstrap the bridge and
	 *        to satisfy the injected method's authorisation check.
	 * @param bridgeBytecodeGenerator generates bridge class bytecode for a given fully-qualified
	 *        class name. The generated class must have a {@code package-private static} method
	 *        named {@value #BRIDGE_METHOD_NAME} with the signature described on that constant
	 *        that performs the {@code defineHiddenClass}+instantiate and returns the instance,
	 *        gated on the supplied lookup being full-privilege and belonging to this SPI's module,
	 *        plus a {@code package-private static final MethodHandle} field named
	 *        {@value #BRIDGE_HANDLE_FIELD_NAME}, initialized in the class initializer with a
	 *        handle to that method (the bridge resolves it with its own full-privilege lookup).
	 */
	public CrossClassLoaderLookupBridge(Lookup callerLookup, Function<String, byte[]> bridgeBytecodeGenerator) {
		if ( !callerLookup.hasFullPrivilegeAccess() ) {
			throw new IllegalArgumentException( "callerLookup must have full-privilege access" );
		}
		this.callerLookup = callerLookup;
		this.bridgeBytecodeGenerator = bridgeBytecodeGenerator;
	}

	/**
	 * Defines the given accessor {@code bytecode} as a hidden {@code NESTMATE} of
	 * {@code targetClass} and returns a freshly constructed instance using the no-arg
	 * constructor.
	 *
	 * @param callerProof a full-privilege lookup from the same module that created this
	 *        bridge, proving the caller's identity. A caller that passes this check
	 *        necessarily has the capability to perform the same operation themselves
	 *        (via {@code defineClass}), so the bridge is a convenience, not a privilege
	 *        escalation.
	 * @return an instance of the freshly defined accessor class
	 * @throws IllegalAccessError if the caller proof is invalid
	 * @throws Exception if the class cannot be defined or instantiated
	 */
	public Object defineAccessor(Lookup callerProof, Class<?> targetClass, byte[] bytecode) throws Exception {
		return defineAccessor( callerProof, targetClass, bytecode, new Class<?>[0] );
	}

	/**
	 * Defines the given accessor {@code bytecode} as a hidden {@code NESTMATE} of
	 * {@code targetClass} and returns a freshly constructed instance using a constructor
	 * matching the supplied {@code parameterTypes}.
	 * <p>
	 * Instantiation uses {@link Lookup#findConstructor} via {@code privateLookupIn}
	 * rather than reflection, so it works even when the hidden class is in a foreign
	 * module (reflection checks the calling class's module access, which would fail).
	 *
	 * @param callerProof a full-privilege lookup from the same module that created this
	 *        bridge, proving the caller's identity
	 * @param parameterTypes the constructor parameter types (empty array for no-arg)
	 * @param constructorArgs the arguments to pass to the constructor
	 * @return an instance of the freshly defined accessor class
	 * @throws IllegalAccessError if the caller proof is invalid
	 * @throws Exception if the class cannot be defined or instantiated
	 */
	public Object defineAccessor(Lookup callerProof, Class<?> targetClass, byte[] bytecode,
			Class<?>[] parameterTypes, Object... constructorArgs)
			throws Exception {
		final Class<?> accessorClass = defineAccessorClass( callerProof, targetClass, bytecode );
		final Lookup targetLookup = MethodHandles.privateLookupIn( targetClass, callerLookup );
		final MethodType ctorType = MethodType.methodType( void.class, parameterTypes );
		try {
			return targetLookup.findConstructor( accessorClass, ctorType )
					.invokeWithArguments( constructorArgs );
		}
		catch (Exception | Error e) {
			throw e;
		}
		catch (Throwable t) {
			throw new IllegalStateException( "Failed to instantiate accessor for " + targetClass.getName(), t );
		}
	}

	private Class<?> defineAccessorClass(Lookup callerProof, Class<?> targetClass, byte[] bytecode) throws Exception {
		verifyCallerIdentity( callerProof );
		if ( targetClass.getClassLoader() == callerLookup.lookupClass().getClassLoader()
				&& targetClass.getModule() == callerLookup.lookupClass().getModule() ) {
			final Lookup targetLookup = MethodHandles.privateLookupIn( targetClass, callerLookup );
			return targetLookup
					.defineHiddenClass( bytecode, true, Lookup.ClassOption.NESTMATE )
					.lookupClass();
		}
		final MethodHandle bridge = bridgeHandles.get( targetClass );
		try {
			return (Class<?>) bridge.invoke( callerProof, targetClass, bytecode );
		}
		catch (Exception | Error e) {
			throw e;
		}
		catch (Throwable t) {
			throw new IllegalStateException( "Bridge invocation failed for " + targetClass.getName(), t );
		}
	}

	private void verifyCallerIdentity(Lookup callerProof) {
		if ( !callerProof.hasFullPrivilegeAccess() ) {
			throw new IllegalAccessError( "Caller lookup does not have full-privilege access" );
		}
		if ( callerProof.lookupClass().getModule() != callerLookup.lookupClass().getModule() ) {
			throw new IllegalAccessError(
					"Caller lookup is from a different module than the bridge owner" );
		}
	}

	private MethodHandle createBridgeHandle(Class<?> targetClass) {
		try {
			final Lookup crossClLookup = MethodHandles.privateLookupIn( targetClass, callerLookup );
			final String pkg = targetClass.getPackageName();
			final String bridgeClassName = pkg.isEmpty()
					? BRIDGE_CLASS_SIMPLE_NAME
					: pkg + "." + BRIDGE_CLASS_SIMPLE_NAME;
			Class<?> bridgeClass = findBridgeClass( crossClLookup, bridgeClassName );
			if ( bridgeClass == null ) {
				// Absent, or shadowed by a same-named class from an ancestor loader (findClass
				// rejects those: they live in a different runtime package). Ours must live in
				// the target's runtime package, so define it there.
				bridgeClass = defineBridgeClass( crossClLookup, bridgeClassName );
			}
			// PACKAGE access (retained across the module boundary) is sufficient to read the
			// package-private handle field; the handle itself was resolved by the bridge with
			// its own full-privilege lookup, so all classes in this package share it.
			return (MethodHandle) crossClLookup
					.findStaticGetter( bridgeClass, BRIDGE_HANDLE_FIELD_NAME, MethodHandle.class )
					.invoke();
		}
		catch (IllegalAccessException e) {
			throw new AccessorException(
					"Cannot create lookup bridge for classloader of " + targetClass.getName()
							+ ": privateLookupIn failed across module boundary",
					e );
		}
		catch (Throwable t) {
			// invoke() declares Throwable: this also covers errors raised while initializing
			// the bridge class itself.
			if ( t instanceof Error ) {
				throw (Error) t;
			}
			throw new AccessorException(
					"Failed to create lookup bridge for classloader of " + targetClass.getName(), t );
		}
	}

	private Class<?> defineBridgeClass(Lookup crossClLookup, String bridgeClassName)
			throws IllegalAccessException {
		try {
			return crossClLookup.defineClass( bridgeBytecodeGenerator.apply( bridgeClassName ) );
		}
		catch (LinkageError e) {
			// Another factory instance already defined the bridge in this runtime package --
			// reuse it (defineClass creates non-hidden named classes, so a second defineClass
			// for the same name throws LinkageError). findClass sees a class that is already
			// loaded in this runtime package before parent delegation, so it finds ours without
			// needing a shared cache.
			try {
				return crossClLookup.findClass( bridgeClassName );
			}
			catch (ClassNotFoundException | IllegalAccessException ex) {
				throw new IllegalStateException(
						"Bridge class '" + bridgeClassName + "' was defined but cannot be found "
								+ "via lookup -- possibly shadowed by an ancestor classloader",
						e );
			}
		}
	}

	private static Class<?> findBridgeClass(Lookup lookup, String bridgeClassName) {
		try {
			return lookup.findClass( bridgeClassName );
		}
		catch (ClassNotFoundException | IllegalAccessException e) {
			// Absent, or present only in an ancestor classloader (different runtime package).
			return null;
		}
	}
}
