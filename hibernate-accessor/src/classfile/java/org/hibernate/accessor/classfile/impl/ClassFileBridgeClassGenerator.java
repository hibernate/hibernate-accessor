/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.classfile.impl;

import static org.hibernate.accessor.classfile.impl.ClassFileUtils.CD_BYTE_ARRAY;
import static org.hibernate.accessor.classfile.impl.ClassFileUtils.CLASS_INIT_NAME;
import static org.hibernate.accessor.classfile.impl.ClassFileUtils.CONSTRUCTOR_NAME;
import static org.hibernate.accessor.classfile.impl.ClassFileUtils.GENERATED_CLASS_MAJOR_VERSION;
import static org.hibernate.accessor.classfile.impl.ClassFileUtils.generatedClassDesc;
import static org.hibernate.accessor.classfile.impl.ClassFileUtils.methodTypeDesc;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;

import org.hibernate.accessor.spi.CrossClassLoaderLookupBridge;

import io.smallrye.classfile.ClassFile;
import io.smallrye.classfile.Label;

final class ClassFileBridgeClassGenerator {

	private static final ClassDesc CD_LOOKUP = ClassDesc.of( "java.lang.invoke.MethodHandles$Lookup" );
	private static final ClassDesc CD_CLASS_OPTION = ClassDesc.of( "java.lang.invoke.MethodHandles$Lookup$ClassOption" );
	private static final ClassDesc CD_METHOD_HANDLE = ClassDesc.of( "java.lang.invoke.MethodHandle" );
	private static final ClassDesc CD_METHOD_HANDLES = ClassDesc.of( "java.lang.invoke.MethodHandles" );
	private static final ClassDesc CD_METHOD_TYPE = ClassDesc.of( "java.lang.invoke.MethodType" );
	private static final ClassDesc CD_NO_SUCH_METHOD = ClassDesc.of( "java.lang.NoSuchMethodException" );
	private static final ClassDesc CD_ILLEGAL_ACCESS = ClassDesc.of( "java.lang.IllegalAccessException" );
	private static final ClassDesc CD_ILLEGAL_ACCESS_ERROR = ClassDesc.of( "java.lang.IllegalAccessError" );
	private static final ClassDesc CD_EXCEPTION_IN_INIT = ClassDesc.of( "java.lang.ExceptionInInitializerError" );

	private ClassFileBridgeClassGenerator() {
	}

	static byte[] generate(String className) {
		String internalName = className.replace( '.', '/' );
		ClassDesc bridgeDesc = generatedClassDesc( internalName );

		return ClassFile.of().build( bridgeDesc, clb -> {
			clb.with( GENERATED_CLASS_MAJOR_VERSION );
			clb.withFlags( ClassFile.ACC_FINAL | ClassFile.ACC_SUPER | ClassFile.ACC_SYNTHETIC );
			clb.withSuperclass( ConstantDescs.CD_Object );

			clb.withField(
					CrossClassLoaderLookupBridge.BRIDGE_HANDLE_FIELD_NAME,
					CD_METHOD_HANDLE,
					ClassFile.ACC_STATIC | ClassFile.ACC_FINAL
			);

			generateClassInitializer( clb, bridgeDesc );
			generateDefineAccessorMethod( clb, bridgeDesc );
		} );
	}

	private static void generateClassInitializer(io.smallrye.classfile.ClassBuilder clb, ClassDesc bridgeDesc) {
		clb.withMethod( CLASS_INIT_NAME, methodTypeDesc( ConstantDescs.CD_void ), ClassFile.ACC_STATIC, mb -> mb.withCode( cb -> {
			Label tryStart = cb.newLabel();
			Label tryEnd = cb.newLabel();
			Label catchHandler = cb.newLabel();

			cb.exceptionCatch( tryStart, tryEnd, catchHandler, CD_NO_SUCH_METHOD );
			cb.exceptionCatch( tryStart, tryEnd, catchHandler, CD_ILLEGAL_ACCESS );

			cb.labelBinding( tryStart );
			// MethodHandles.lookup()
			cb.invokestatic( CD_METHOD_HANDLES, "lookup", methodTypeDesc( CD_LOOKUP ) );
			// bridge class constant
			cb.loadConstant( bridgeDesc );
			// method name
			cb.loadConstant( CrossClassLoaderLookupBridge.BRIDGE_METHOD_NAME );
			// MethodType.methodType(Object.class, new Class[]{Lookup.class, Class.class, byte[].class})
			cb.loadConstant( ConstantDescs.CD_Object );
			cb.loadConstant( 3 );
			cb.anewarray( ConstantDescs.CD_Class );
			cb.dup();
			cb.loadConstant( 0 );
			cb.loadConstant( CD_LOOKUP );
			cb.aastore();
			cb.dup();
			cb.loadConstant( 1 );
			cb.loadConstant( ConstantDescs.CD_Class );
			cb.aastore();
			cb.dup();
			cb.loadConstant( 2 );
			cb.loadConstant( CD_BYTE_ARRAY );
			cb.aastore();
			cb.invokestatic( CD_METHOD_TYPE, "methodType",
					MethodTypeDesc.of( CD_METHOD_TYPE, ConstantDescs.CD_Class, ConstantDescs.CD_Class.arrayType() ) );
			// lookup.findStatic(bridgeClass, methodName, methodType)
			cb.invokevirtual( CD_LOOKUP, "findStatic",
					MethodTypeDesc.of( CD_METHOD_HANDLE, ConstantDescs.CD_Class, ConstantDescs.CD_String, CD_METHOD_TYPE ) );
			cb.putstatic( bridgeDesc, CrossClassLoaderLookupBridge.BRIDGE_HANDLE_FIELD_NAME, CD_METHOD_HANDLE );
			cb.labelBinding( tryEnd );
			cb.return_();

			// catch: throw new ExceptionInInitializerError(e)
			cb.labelBinding( catchHandler );
			cb.new_( CD_EXCEPTION_IN_INIT );
			cb.dup_x1();
			cb.swap();
			cb.invokespecial( CD_EXCEPTION_IN_INIT, CONSTRUCTOR_NAME,
					MethodTypeDesc.of( ConstantDescs.CD_void, ConstantDescs.CD_Throwable ) );
			cb.athrow();
		} )
		);
	}

	private static void generateDefineAccessorMethod(io.smallrye.classfile.ClassBuilder clb, ClassDesc bridgeDesc) {
		MethodTypeDesc defineMethodType = MethodTypeDesc.of(
				ConstantDescs.CD_Object, CD_LOOKUP, ConstantDescs.CD_Class, CD_BYTE_ARRAY
		);

		clb.withMethod( CrossClassLoaderLookupBridge.BRIDGE_METHOD_NAME, defineMethodType, ClassFile.ACC_STATIC, mb -> mb.withCode( cb -> {
			// Access check: MethodHandles.privateLookupIn(bridge.class, proof)
			Label tryStart = cb.newLabel();
			Label tryEnd = cb.newLabel();
			Label catchHandler = cb.newLabel();
			Label afterCheck = cb.newLabel();

			cb.exceptionCatch( tryStart, tryEnd, catchHandler, CD_ILLEGAL_ACCESS );

			cb.labelBinding( tryStart );
			cb.loadConstant( bridgeDesc );
			cb.aload( 0 );
			cb.invokestatic( CD_METHOD_HANDLES, "privateLookupIn",
					MethodTypeDesc.of( CD_LOOKUP, ConstantDescs.CD_Class, CD_LOOKUP ) );
			cb.pop();
			// Authorize the requested target as well as the bridge package.
			cb.aload( 1 );
			cb.aload( 0 );
			cb.invokestatic( CD_METHOD_HANDLES, "privateLookupIn",
					MethodTypeDesc.of( CD_LOOKUP, ConstantDescs.CD_Class, CD_LOOKUP ) );
			cb.pop();
			cb.labelBinding( tryEnd );
			cb.goto_( afterCheck );

			cb.labelBinding( catchHandler );
			cb.pop();
			// throw new IllegalAccessError(message)
			cb.new_( CD_ILLEGAL_ACCESS_ERROR );
			cb.dup();
			cb.loadConstant( "caller's lookup cannot access the bridge or target package" );
			cb.invokespecial( CD_ILLEGAL_ACCESS_ERROR, CONSTRUCTOR_NAME,
					MethodTypeDesc.of( ConstantDescs.CD_void, ConstantDescs.CD_String ) );
			cb.athrow();

			cb.labelBinding( afterCheck );

			// Lookup here = MethodHandles.lookup();
			cb.invokestatic( CD_METHOD_HANDLES, "lookup", methodTypeDesc( CD_LOOKUP ) );
			cb.astore( 3 );

			// Lookup tl = MethodHandles.privateLookupIn(target, here);
			cb.aload( 1 );
			cb.aload( 3 );
			cb.invokestatic( CD_METHOD_HANDLES, "privateLookupIn",
					MethodTypeDesc.of( CD_LOOKUP, ConstantDescs.CD_Class, CD_LOOKUP ) );
			cb.astore( 4 );

			// tl.defineHiddenClass(bytecode, true, new ClassOption[]{NESTMATE}).lookupClass()
			cb.aload( 4 );
			cb.aload( 2 );
			cb.loadConstant( 1 );
			cb.loadConstant( 1 );
			cb.anewarray( CD_CLASS_OPTION );
			cb.dup();
			cb.loadConstant( 0 );
			cb.getstatic( CD_CLASS_OPTION, "NESTMATE", CD_CLASS_OPTION );
			cb.aastore();
			cb.invokevirtual( CD_LOOKUP, "defineHiddenClass",
					MethodTypeDesc.of( CD_LOOKUP, CD_BYTE_ARRAY, ConstantDescs.CD_boolean, CD_CLASS_OPTION.arrayType() ) );
			cb.invokevirtual( CD_LOOKUP, "lookupClass",
					methodTypeDesc( ConstantDescs.CD_Class ) );

			cb.areturn();
		} )
		);
	}
}
