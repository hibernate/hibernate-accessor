/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.spi;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;

import org.hibernate.accessor.AccessorException;

/**
 * Performs module and reflection access using the original lookup supplied by a framework.
 * Caller-sensitive method handles retain that framework's identity when invoked by this library.
 * Read edges are added lazily, because entity modules need not be known at factory bootstrap.
 * Native integrations should supply a framework-owned {@link AccessContext} instead:
 * native VMs may not preserve the lookup's caller identity for these JDK methods.
 */
public final class LookupAccess implements AccessContext {
	private final MethodHandles.Lookup lookup;
	private final MethodHandle addReads;
	private final MethodHandle makeAccessible;

	public LookupAccess(MethodHandles.Lookup lookup) {
		this.lookup = lookup;
		if ( ( lookup.lookupModes() & MethodHandles.Lookup.ORIGINAL ) != 0 ) {
			try {
				addReads = lookup.findVirtual( Module.class, "addReads",
						MethodType.methodType( Module.class, Module.class ) )
						.bindTo( lookup.lookupClass().getModule() );
				makeAccessible = lookup.findStatic( AccessibleObject.class, "setAccessible",
						MethodType.methodType( void.class, AccessibleObject[].class, boolean.class ) );
			}
			catch (NoSuchMethodException | IllegalAccessException e) {
				throw new AccessorException( "Cannot prepare framework access for " + lookup, e );
			}
		}
		else {
			// Preserve support for derived/restricted lookups and the historical library reflection fallback.
			addReads = null;
			makeAccessible = null;
		}
	}

	@Override
	public MethodHandles.Lookup lookup() {
		return lookup;
	}

	@Override
	public MethodHandles.Lookup privateLookup(Class<?> target) throws IllegalAccessException {
		ensureReads( target.getModule() );
		return MethodHandles.privateLookupIn( target, lookup );
	}

	@Override
	public void ensureReads(Module target) {
		if ( addReads != null && !lookup.lookupClass().getModule().canRead( target ) ) {
			try {
				addReads.invoke( target );
			}
			catch (RuntimeException | Error e) {
				throw e;
			}
			catch (Throwable t) {
				throw new AccessorException( "Cannot add framework read edge to " + target, t );
			}
		}
	}

	@Override
	public void makeAccessible(AccessibleObject member) {
		if ( makeAccessible == null ) {
			member.setAccessible( true );
			return;
		}
		try {
			makeAccessible.invokeExact( new AccessibleObject[] { member }, true );
		}
		catch (RuntimeException | Error e) {
			throw e;
		}
		catch (Throwable t) {
			throw new AccessorException( "Cannot enable framework reflection access to " + member, t );
		}
	}
}
