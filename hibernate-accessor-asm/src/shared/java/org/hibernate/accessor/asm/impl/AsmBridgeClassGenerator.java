/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.asm.impl;

import org.hibernate.accessor.spi.CrossClassLoaderLookupBridge;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Generates the bridge class bytecode used by {@link CrossClassLoaderLookupBridge}
 * to define a generated accessor as a hidden nestmate of a target class in a foreign
 * classloader, without ever letting a full-privilege
 * {@link java.lang.invoke.MethodHandles.Lookup} escape. Uses standalone ASM.
 * <p>
 * The generated class is equivalent to:
 * <pre>{@code
 * final class $$HibernateAccessorBridge {
 *     static final MethodHandle DEFINE_ACCESSOR_MH;
 *     static {
 *         try {
 *             DEFINE_ACCESSOR_MH = MethodHandles.lookup().findStatic(
 *                     $$HibernateAccessorBridge.class, "$$defineAccessor",
 *                     MethodType.methodType( Object.class, Lookup.class, Class.class, byte[].class ));
 *         }
 *         catch (NoSuchMethodException | IllegalAccessException e) {
 *             throw new ExceptionInInitializerError( e );
 *         }
 *     }
 *
 *     static Object $$defineAccessor(MethodHandles.Lookup proof, Class<?> target, byte[] bytecode)
 *             throws Throwable {
 *         MethodHandles.privateLookupIn( $$HibernateAccessorBridge.class, proof ); // access check
 *         MethodHandles.Lookup here = MethodHandles.lookup();            // full-priv, target module
 *         MethodHandles.Lookup tl   = MethodHandles.privateLookupIn( target, here );
 *         Class<?> a = tl.defineHiddenClass( bytecode, true, NESTMATE ).lookupClass();
 *         return a;
 *     }
 * }
 * }</pre>
 * The class and its method are package-private. The method verifies that the caller's
 * lookup can access the bridge class's package via
 * {@link java.lang.invoke.MethodHandles#privateLookupIn}: if the caller could reach
 * the package, they could already inject their own bridge via
 * {@link java.lang.invoke.MethodHandles.Lookup#defineClass}, so this bridge is not a
 * privilege escalation.
 * <p>
 * The {@code DEFINE_ACCESSOR_MH} field holds the bridge's own
 * {@link java.lang.invoke.MethodHandle}, resolved once in the class initializer:
 * {@link java.lang.invoke.MethodHandles#lookup()}
 * is caller-sensitive, so inside {@code <clinit>} it yields full-privilege access for the
 * bridge class itself and can resolve its own package-private method. Injecting the handle
 * this way means every user of the bridge reads one shared instance instead of each
 * resolving the method again with its own lookup.
 *
 * @see CrossClassLoaderLookupBridge
 */
final class AsmBridgeClassGenerator {

	private static final String LOOKUP = "java/lang/invoke/MethodHandles$Lookup";
	private static final String LOOKUP_DESC = "Ljava/lang/invoke/MethodHandles$Lookup;";
	private static final String CLASS_OPTION = "java/lang/invoke/MethodHandles$Lookup$ClassOption";
	private static final String CLASS_OPTION_DESC = "Ljava/lang/invoke/MethodHandles$Lookup$ClassOption;";
	private static final String METHOD_HANDLE_DESC = "Ljava/lang/invoke/MethodHandle;";

	private AsmBridgeClassGenerator() {
	}

	static byte[] generate(String className) {
		final String internalName = className.replace( '.', '/' );

		// COMPUTE_FRAMES so we don't have to hand-compute stack map frames for the branches.
		final ClassWriter cw = new ClassWriter( ClassWriter.COMPUTE_FRAMES );
		// package-private final class extending Object
		cw.visit( Opcodes.V17, Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC, internalName,
				null, "java/lang/Object", null );

		// static final MethodHandle DEFINE_ACCESSOR_MH; (package-private)
		cw.visitField( Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
				CrossClassLoaderLookupBridge.BRIDGE_HANDLE_FIELD_NAME, METHOD_HANDLE_DESC, null, null );

		generateClassInitializer( cw, internalName );

		generateDefineAccessorMethod( cw, internalName );

		cw.visitEnd();
		return cw.toByteArray();
	}

	// static { DEFINE_ACCESSOR_MH = MethodHandles.lookup().findStatic(...); }
	private static void generateClassInitializer(ClassWriter cw, String internalName) {
		final MethodVisitor mv = cw.visitMethod( Opcodes.ACC_STATIC, "<clinit>", "()V", null, null );
		mv.visitCode();

		final Label tryStart = new Label();
		final Label tryEnd = new Label();
		final Label catchHandler = new Label();
		mv.visitTryCatchBlock( tryStart, tryEnd, catchHandler, "java/lang/NoSuchMethodException" );
		mv.visitTryCatchBlock( tryStart, tryEnd, catchHandler, "java/lang/IllegalAccessException" );

		mv.visitLabel( tryStart );
		// MethodHandles.lookup() -- caller sensitive: full privilege for this bridge class.
		mv.visitMethodInsn( Opcodes.INVOKESTATIC, "java/lang/invoke/MethodHandles", "lookup",
				"()" + LOOKUP_DESC, false );
		mv.visitLdcInsn( Type.getObjectType( internalName ) );
		mv.visitLdcInsn( CrossClassLoaderLookupBridge.BRIDGE_METHOD_NAME );
		// MethodType.methodType( Object.class, Lookup.class, Class.class, byte[].class )
		mv.visitLdcInsn( Type.getObjectType( "java/lang/Object" ) );
		mv.visitInsn( Opcodes.ICONST_3 );
		mv.visitTypeInsn( Opcodes.ANEWARRAY, "java/lang/Class" );
		mv.visitInsn( Opcodes.DUP );
		mv.visitInsn( Opcodes.ICONST_0 );
		mv.visitLdcInsn( Type.getObjectType( LOOKUP ) );
		mv.visitInsn( Opcodes.AASTORE );
		mv.visitInsn( Opcodes.DUP );
		mv.visitInsn( Opcodes.ICONST_1 );
		mv.visitLdcInsn( Type.getObjectType( "java/lang/Class" ) );
		mv.visitInsn( Opcodes.AASTORE );
		mv.visitInsn( Opcodes.DUP );
		mv.visitInsn( Opcodes.ICONST_2 );
		mv.visitLdcInsn( Type.getType( "[B" ) );
		mv.visitInsn( Opcodes.AASTORE );
		mv.visitMethodInsn( Opcodes.INVOKESTATIC, "java/lang/invoke/MethodType", "methodType",
				"(Ljava/lang/Class;[Ljava/lang/Class;)Ljava/lang/invoke/MethodType;", false );
		mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, LOOKUP, "findStatic",
				"(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)" + METHOD_HANDLE_DESC,
				false );
		mv.visitFieldInsn( Opcodes.PUTSTATIC, internalName,
				CrossClassLoaderLookupBridge.BRIDGE_HANDLE_FIELD_NAME, METHOD_HANDLE_DESC );
		mv.visitLabel( tryEnd );
		mv.visitInsn( Opcodes.RETURN );

		// catch (NoSuchMethodException | IllegalAccessException e): throw new ExceptionInInitializerError(e)
		mv.visitLabel( catchHandler );
		mv.visitTypeInsn( Opcodes.NEW, "java/lang/ExceptionInInitializerError" );
		mv.visitInsn( Opcodes.DUP_X1 );
		mv.visitInsn( Opcodes.SWAP );
		mv.visitMethodInsn( Opcodes.INVOKESPECIAL, "java/lang/ExceptionInInitializerError", "<init>",
				"(Ljava/lang/Throwable;)V", false );
		mv.visitInsn( Opcodes.ATHROW );

		mv.visitMaxs( 0, 0 );
		mv.visitEnd();
	}

	// static Object $$defineAccessor(Lookup proof, Class<?> target, byte[] bytecode)
	private static void generateDefineAccessorMethod(ClassWriter cw, String internalName) {
		final MethodVisitor mv = cw.visitMethod( Opcodes.ACC_STATIC,
				CrossClassLoaderLookupBridge.BRIDGE_METHOD_NAME,
				"(" + LOOKUP_DESC + "Ljava/lang/Class;[B)Ljava/lang/Object;", null, null );
		mv.visitCode();

		// Access check: MethodHandles.privateLookupIn( $$bridge.class, proof )
		// If the caller's lookup can reach this package, they could already inject
		// their own bridge — so this one is not a privilege escalation.
		final Label tryStart = new Label();
		final Label tryEnd = new Label();
		final Label catchHandler = new Label();
		final Label afterCheck = new Label();
		mv.visitTryCatchBlock( tryStart, tryEnd, catchHandler,
				"java/lang/IllegalAccessException" );

		mv.visitLabel( tryStart );
		mv.visitLdcInsn( Type.getObjectType( internalName ) );
		mv.visitVarInsn( Opcodes.ALOAD, 0 );
		mv.visitMethodInsn( Opcodes.INVOKESTATIC, "java/lang/invoke/MethodHandles", "privateLookupIn",
				"(Ljava/lang/Class;" + LOOKUP_DESC + ")" + LOOKUP_DESC, false );
		mv.visitInsn( Opcodes.POP );
		mv.visitLabel( tryEnd );
		mv.visitJumpInsn( Opcodes.GOTO, afterCheck );

		mv.visitLabel( catchHandler );
		mv.visitInsn( Opcodes.POP );
		throwIllegalAccessError( mv, "caller's lookup cannot access the bridge's package" );

		mv.visitLabel( afterCheck );

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

		// return a;
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
