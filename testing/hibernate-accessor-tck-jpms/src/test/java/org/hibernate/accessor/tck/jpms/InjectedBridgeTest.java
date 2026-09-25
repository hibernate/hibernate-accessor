/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.tck.jpms;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.asm.AsmAccessorFactory;
import org.hibernate.accessor.bytebuddy.ByteBuddyAccessorFactory;
import org.hibernate.accessor.spi.CrossClassLoaderLookupBridge;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InjectedBridgeTest {
	@ParameterizedTest
	@ValueSource(strings = { "asm", "bytebuddy", "classfile" })
	void directBridgeChecksProofAndTargetBeforeProcessingBytecode(String strategy) throws Throwable {
		var proof = MethodHandles.lookup();
		AccessorFactory factory = switch ( strategy ) {
			case "asm" -> AsmAccessorFactory.factory( proof );
			case "bytebuddy" -> ByteBuddyAccessorFactory.factory( proof );
			default -> AccessorFactory.classFile( proof );
		};
		var target = Class.forName( "org.hibernate.accessor.tck.jpms." + strategy + ".GateEntity" );
		var reader = factory.multiValueReader( target, target.getDeclaredField( "value" ) );
		// A fallback would either fail module access or lack this hidden generated class.
		assertThat( reader.getClass().isHidden() ).isTrue();
		var lookup = MethodHandles.privateLookupIn( target, proof );
		var entity = lookup.findConstructor( target, MethodType.methodType( void.class ) ).invoke();
		assertThat( reader.get( entity ) ).containsExactly( "allowed" );
		var bridgeClass = lookup.findClass( target.getPackageName() + "." + CrossClassLoaderLookupBridge.BRIDGE_CLASS_SIMPLE_NAME );
		var bridge = (MethodHandle) lookup.findStaticGetter( bridgeClass,
				CrossClassLoaderLookupBridge.BRIDGE_HANDLE_FIELD_NAME, MethodHandle.class ).invoke();
		var closed = Class.forName( "org.hibernate.accessor.tck.jpms.closed.GateEntity" );
		assertThatThrownBy( () -> bridge.invoke( proof, closed, new byte[0] ) )
				.isInstanceOf( IllegalAccessError.class );
		assertThatThrownBy( () -> bridge.invoke( MethodHandles.publicLookup(), target, new byte[0] ) )
				.isInstanceOf( IllegalAccessError.class );
		assertThatThrownBy( () -> bridge.invoke( lookup, target, new byte[0] ) )
				.isInstanceOf( IllegalAccessError.class );
		// The library module has not been granted this qualified opens.
		assertThatThrownBy( () -> bridge.invoke( org.hibernate.accessor.tck.jpms.entities.BridgeGateProbe
				.entityModuleLookup().dropLookupMode( MethodHandles.Lookup.PRIVATE ), target, new byte[0] ) )
				.isInstanceOf( IllegalAccessError.class );
		// Trusted bytecode can acquire and return its own full module authority.
		byte[] bytes;
		try ( var stream = target.getResourceAsStream( "GateEntity.class" ) ) {
			bytes = stream.readAllBytes();
		}
		var hidden = (Class<?>) bridge.invoke( proof, target, bytes );
		var leaked = (MethodHandles.Lookup) MethodHandles.privateLookupIn( hidden, proof )
				.findStatic( hidden, "lookup", MethodType.methodType( MethodHandles.Lookup.class ) ).invoke();
		assertThat( leaked.hasFullPrivilegeAccess() ).isTrue();
		assertThat( leaked.lookupClass().getModule() ).isSameAs( target.getModule() );
		// A valid proof reaches bytecode verification: the gate itself accepts it.
		assertThatThrownBy( () -> bridge.invoke( proof, target, new byte[0] ) )
				.isInstanceOf( ClassFormatError.class );
	}
}
