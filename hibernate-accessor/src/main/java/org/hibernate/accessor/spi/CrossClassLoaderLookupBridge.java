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
 * Defines trusted accessor bytecode as a hidden nestmate of a target class and
 * constructs an instance, including across classloader and JPMS module boundaries.
 * <p>
 * {@link MethodHandles#privateLookupIn(Class, Lookup)} drops {@code MODULE} access
 * when crossing modules. Such a lookup can define a named class in an open package,
 * but cannot call {@link Lookup#defineHiddenClass(byte[], boolean, Lookup.ClassOption...)}.
 * This bridge injects one package-private named helper per target runtime package.
 * The helper obtains its own full-privilege lookup, defines a hidden nestmate of the
 * target, and returns its class. Within the caller's own module no helper is needed.
 * <p>
 * <b>Security contract:</b> this is a trusted code-generation facility, not a sandbox.
 * The public entry point requires a full-privilege proof from the module of the
 * lookup supplied at construction. The injected method independently checks that
 * the proof can perform {@code privateLookupIn} on both the bridge and the requested
 * target before processing bytecode. It is shared between authorized callers and
 * does not bind itself to the first factory's module.
 * <p>
 * The helper does not directly return or store its full-privilege lookup. However,
 * supplied bytecode runs with the target module's authority and can obtain and
 * publish its own full-privilege lookup, including from a class initializer. The
 * cached method handle is a reusable, proof-gated code-definition capability.
 * Neither it nor arbitrary supplied bytecode should be described as confined to a
 * single accessor operation. A caller able to inject a named class into an opened
 * package can already obtain the same module authority without this helper.
 * <p>
 * A qualified {@code opens P to caller.module} permits callers in that module to
 * acquire the helper handle. Other modules without access to P cannot acquire it
 * directly; code in the target module and code given an authorized lookup can.
 * Opening P is not isolation from the rest of the target module against trusted
 * callers who inject code. On the classpath each loader has its own unnamed module;
 * different loaders therefore use the bridge even without named JPMS modules.
 * <p>
 * <b>Lifetime:</b> the named helper lives as long as its target loader (one per
 * runtime package), while generated hidden classes use the default weak linkage.
 * Generated code must be able to resolve its referenced interfaces and the target
 * module must read their modules; opening a package alone does not add read edges.
 * A {@link ClassValue} associates target classes with the helper's shared handle;
 * the handle refers only to the target helper and JDK types. This association does
 * not root disposable target classes from this factory. The factory does retain its
 * configured caller lookup and bytecode-generator function, so their loaders remain
 * reachable for the factory's lifetime. Returned accessors retain their target types.
 *
 * @see <a href="https://bugs.openjdk.org/browse/JDK-8228624">JDK-8228624</a>
 */
public final class CrossClassLoaderLookupBridge {

	/**
	 * Simple name of the bridge class injected into foreign modules. The bridge is always
	 * placed in the target's own runtime package under this fixed name, making its
	 * location predictable: it can be rediscovered via {@link Lookup#findClass(String)}
	 * without any lookup tables. Discovery explicitly checks loader and module identity
	 * so even a public same-named class from an ancestor loader cannot be reused.
	 */
	public static final String BRIDGE_CLASS_SIMPLE_NAME = "$$HibernateAccessorBridge";

	/**
	 * Name of the package-private static method on the bridge class that defines the
	 * generated accessor as a hidden nestmate of the target class and returns the
	 * defined class. Its signature is
	 * {@code static Object <name>(MethodHandles.Lookup proof, Class<?> target, byte[] bytecode)}.
	 * The method verifies that the supplied {@code proof} can access the bridge class's
	 * package and the requested target via {@link MethodHandles#privateLookupIn}.
	 * Supplied bytecode must be trusted as described in the class security contract.
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
	 *        that defines a hidden nestmate and returns its Class. Before definition it must
	 *        check private lookup access to both the bridge and the target using the supplied proof,
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
		if ( callerProof == null || !callerProof.hasFullPrivilegeAccess() ) {
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
			// Define before discovery. Looking up an ancestor's same-named class first
			// can register this loader as an initiating loader, preventing a local
			// definition with that name even if the ancestor class is inaccessible.
			Class<?> bridgeClass = defineBridgeClass( crossClLookup, bridgeClassName );
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
			// A concurrent factory may have defined the helper. Do not mask unrelated
			// linkage failures or reuse a same-named class from an ancestor loader.
			Class<?> bridgeClass = findBridgeClass( crossClLookup, bridgeClassName );
			if ( bridgeClass != null ) {
				return bridgeClass;
			}
			throw e;
		}
	}

	private static Class<?> findBridgeClass(Lookup lookup, String bridgeClassName) {
		try {
			Class<?> bridgeClass = lookup.findClass( bridgeClassName );
			Class<?> target = lookup.lookupClass();
			return bridgeClass.getClassLoader() == target.getClassLoader()
					&& bridgeClass.getModule() == target.getModule() ? bridgeClass : null;
		}
		catch (ClassNotFoundException | IllegalAccessException e) {
			// Absent, or present only in an ancestor classloader (different runtime package).
			return null;
		}
	}
}
