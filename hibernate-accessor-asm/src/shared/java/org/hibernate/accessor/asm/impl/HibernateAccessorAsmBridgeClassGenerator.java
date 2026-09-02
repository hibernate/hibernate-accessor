/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.asm.impl;

import org.hibernate.accessor.spi.CrossClassLoaderLookupBridge;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Generates the bridge class bytecode used by {@link CrossClassLoaderLookupBridge}
 * to define a generated accessor as a hidden nestmate of a target class in a foreign
 * classloader, without ever letting a full-privilege
 * {@link java.lang.invoke.MethodHandles.Lookup} escape. Uses standalone ASM.
 * <p>
 * The generated class is equivalent to:
 * <pre>{@code
 * final class $$HibernateAccessorBridge {
 *     static Object $$defineAccessor(MethodHandles.Lookup proof, Class<?> target, byte[] bytecode)
 *             throws Throwable {
 *         if ( !proof.hasFullPrivilegeAccess() )                 throw new IllegalAccessError( ... );
 *         String name = proof.lookupClass().getModule().getName();
 *         if ( !name.isEmpty() && !AUTHORISED_MODULE_NAME.equals( name ) ) throw new IllegalAccessError( ... );
 *         MethodHandles.Lookup here = MethodHandles.lookup();            // full-priv, target module
 *         MethodHandles.Lookup tl   = MethodHandles.privateLookupIn( target, here );
 *         Class<?> a = tl.defineHiddenClass( bytecode, true, NESTMATE ).lookupClass();
 *         return a.getDeclaredConstructor().newInstance();
 *     }
 * }
 * }</pre>
 * The class and its method are package-private so the method cannot be reached from any
 * other module except through a lookup that already has {@code PACKAGE} access to the
 * target package. The module check compares the caller's module <em>name</em> (baked into
 * the bytecode) rather than the module instance, so the generated class never references
 * this SPI's classes and can load in a classloader that cannot see them.
 *
 * @see CrossClassLoaderLookupBridge
 */
final class HibernateAccessorAsmBridgeClassGenerator {

	private static final String LOOKUP = "java/lang/invoke/MethodHandles$Lookup";
	private static final String LOOKUP_DESC = "Ljava/lang/invoke/MethodHandles$Lookup;";
	private static final String CLASS_OPTION = "java/lang/invoke/MethodHandles$Lookup$ClassOption";
	private static final String CLASS_OPTION_DESC = "Ljava/lang/invoke/MethodHandles$Lookup$ClassOption;";
	private static final String AUTHORISED_MODULE_NAME = "org.hibernate.accessor";

	private HibernateAccessorAsmBridgeClassGenerator() {
	}

	static byte[] generate(String className) {
		final String internalName = className.replace( '.', '/' );

		// COMPUTE_FRAMES so we don't have to hand-compute stack map frames for the branches.
		final ClassWriter cw = new ClassWriter( ClassWriter.COMPUTE_FRAMES );
		// package-private final class extending Object
		cw.visit( Opcodes.V17, Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC, internalName,
				null, "java/lang/Object", null );

		generateDefineAccessorMethod( cw, internalName );

		cw.visitEnd();
		return cw.toByteArray();
	}

	// static Object $$defineAccessor(Lookup proof, Class<?> target, byte[] bytecode)
	private static void generateDefineAccessorMethod(ClassWriter cw, String internalName) {
		final MethodVisitor mv = cw.visitMethod( Opcodes.ACC_STATIC,
				CrossClassLoaderLookupBridge.BRIDGE_METHOD_NAME,
				"(" + LOOKUP_DESC + "Ljava/lang/Class;[B)Ljava/lang/Object;", null, null );
		mv.visitCode();

		// if ( !proof.hasFullPrivilegeAccess() ) throw new IllegalAccessError( ... );
		final Label hasPrivilege = new Label();
		mv.visitVarInsn( Opcodes.ALOAD, 0 );
		mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, LOOKUP, "hasFullPrivilegeAccess", "()Z", false );
		mv.visitJumpInsn( Opcodes.IFNE, hasPrivilege );
		throwIllegalAccessError( mv, "accessor bridge invoked without a full-privilege lookup" );
		mv.visitLabel( hasPrivilege );

		// Compare the caller module's name against the SPI module's name, baked into the
		// bytecode at generation time. We acknowledge this is weaker than comparing the
		// Module instances: two *different* module instances sharing the same module name
		// (e.g. the same module in two layers, or a duplicate jar) would pass this check.
		// That is acceptable because the caller can only reach the package-private bridge
		// method from its own module, where it can already do anything the bridge does; the
		// name check is defence-in-depth and, importantly, keeps the generated class free of
		// any reference to this SPI's classes so it can load in a classloader that cannot
		// see them.
		// if ( !AUTHORISED_MODULE_NAME.equals( proof.lookupClass().getModule().getName() ) )
		//         throw new IllegalAccessError( ... );
		final Label authorised = new Label();
		mv.visitVarInsn( Opcodes.ALOAD, 0 );
		mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, LOOKUP, "lookupClass", "()Ljava/lang/Class;", false );
		mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getModule", "()Ljava/lang/Module;", false );
		mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "java/lang/Module", "getName", "()Ljava/lang/String;", false );
		mv.visitVarInsn( Opcodes.ASTORE, 6 );

		// if ( name.isEmpty() ) goto authorised;
		mv.visitVarInsn( Opcodes.ALOAD, 6 );
		mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "java/lang/String", "isEmpty", "()Z", false );
		mv.visitJumpInsn( Opcodes.IFNE, authorised );

		// if ( AUTHORISED_MODULE_NAME.equals( name ) ) goto authorised;
		mv.visitLdcInsn( AUTHORISED_MODULE_NAME );
		mv.visitVarInsn( Opcodes.ALOAD, 6 );
		mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "java/lang/Object", "equals",
				"(Ljava/lang/Object;)Z", false );
		mv.visitJumpInsn( Opcodes.IFNE, authorised );
		throwIllegalAccessError( mv, "accessor bridge invoked by an unauthorised module" );
		mv.visitLabel( authorised );

		// Lookup here = MethodHandles.lookup();
		mv.visitMethodInsn( Opcodes.INVOKESTATIC, "java/lang/invoke/MethodHandles", "lookup",
				"()" + LOOKUP_DESC, false );
		mv.visitVarInsn( Opcodes.ASTORE, 3 );

		// Lookup tl = MethodHandles.privateLookupIn( target, here );
		mv.visitVarInsn( Opcodes.ALOAD, 1 );
		mv.visitVarInsn( Opcodes.ALOAD, 3 );
		mv.visitMethodInsn( Opcodes.INVOKESTATIC, "java/lang/invoke/MethodHandles", "privateLookupIn",
				"(Ljava/lang/Class;" + LOOKUP_DESC + ")" + LOOKUP_DESC, false );
		mv.visitVarInsn( Opcodes.ASTORE, 4 );

		// Class<?> a = tl.defineHiddenClass( bytecode, true, new ClassOption[]{ NESTMATE } ).lookupClass();
		mv.visitVarInsn( Opcodes.ALOAD, 4 );
		mv.visitVarInsn( Opcodes.ALOAD, 2 );
		mv.visitInsn( Opcodes.ICONST_1 );
		mv.visitInsn( Opcodes.ICONST_1 );
		mv.visitTypeInsn( Opcodes.ANEWARRAY, CLASS_OPTION );
		mv.visitInsn( Opcodes.DUP );
		mv.visitInsn( Opcodes.ICONST_0 );
		mv.visitFieldInsn( Opcodes.GETSTATIC, CLASS_OPTION, "NESTMATE", CLASS_OPTION_DESC );
		mv.visitInsn( Opcodes.AASTORE );
		mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, LOOKUP, "defineHiddenClass",
				"([BZ[" + CLASS_OPTION_DESC + ")" + LOOKUP_DESC, false );
		mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, LOOKUP, "lookupClass", "()Ljava/lang/Class;", false );
		mv.visitVarInsn( Opcodes.ASTORE, 5 );

		// return a.getDeclaredConstructor().newInstance();
		mv.visitVarInsn( Opcodes.ALOAD, 5 );
		mv.visitInsn( Opcodes.ICONST_0 );
		mv.visitTypeInsn( Opcodes.ANEWARRAY, "java/lang/Class" );
		mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getDeclaredConstructor",
				"([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;", false );
		mv.visitInsn( Opcodes.ICONST_0 );
		mv.visitTypeInsn( Opcodes.ANEWARRAY, "java/lang/Object" );
		mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "java/lang/reflect/Constructor", "newInstance",
				"([Ljava/lang/Object;)Ljava/lang/Object;", false );
		mv.visitInsn( Opcodes.ARETURN );

		mv.visitMaxs( 0, 0 );
		mv.visitEnd();
	}

	private static void throwIllegalAccessError(MethodVisitor mv, String message) {
		mv.visitTypeInsn( Opcodes.NEW, "java/lang/IllegalAccessError" );
		mv.visitInsn( Opcodes.DUP );
		mv.visitLdcInsn( message );
		mv.visitMethodInsn( Opcodes.INVOKESPECIAL, "java/lang/IllegalAccessError", "<init>",
				"(Ljava/lang/String;)V", false );
		mv.visitInsn( Opcodes.ATHROW );
	}
}
