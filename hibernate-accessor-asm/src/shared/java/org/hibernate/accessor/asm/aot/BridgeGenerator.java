package org.hibernate.accessor.asm.aot;

import static org.hibernate.accessor.asm.aot.GenerationUtil.fqcnToName;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.GENERATED_CLASS_VERSION;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.INSTANTIATOR_INTERFACE_INTERNAL;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.METHOD_NAME_FIELD_READER_ACCESSOR;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.METHOD_NAME_FIELD_WRITER_ACCESSOR;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.METHOD_NAME_HOST_CLASS;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.METHOD_NAME_INSTANTIATOR_ACCESSOR;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.METHOD_NAME_METHOD_READER_ACCESSOR;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.METHOD_NAME_METHOD_WRITER_ACCESSOR;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.PREFIX_CREATE_METHOD;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.PREFIX_READ_METHOD;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.PREFIX_WRITE_METHOD;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.READER_INTERFACE_INTERNAL;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.WRITER_INTERFACE_INTERNAL;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Generates a public dispatch bridge in the package of a non-public host class or interface. The host's injected
 * static methods can access its private members, but the generated accessor implementations cannot call those
 * methods directly when the declaring type is not public. The bridge forwards dispatch and accessor lookup calls
 * to the host without exposing the host type in its method signatures.
 * The caller defines this bridge in the same runtime package and classloader as its host.
 */
class BridgeGenerator implements Opcodes {

	private static final String BRIDGE_SUFFIX = "$$HibernateAccessorBridge";

	static String bridgeFqcn(String hostFqcn) {
		return hostFqcn + BRIDGE_SUFFIX;
	}

	byte[] generate(String hostFqcn, boolean hostIsInterface) {
		String bridgeName = fqcnToName( bridgeFqcn( hostFqcn ) );
		String hostName = fqcnToName( hostFqcn );

		ClassWriter cw = new ClassWriter( 0 );
		cw.visit( GENERATED_CLASS_VERSION, ACC_PUBLIC | ACC_SUPER | ACC_SYNTHETIC, bridgeName,
				null, "java/lang/Object", null );

		generateForward( cw, PREFIX_READ_METHOD, "(ILjava/lang/Object;)Ljava/lang/Object;", hostName, hostIsInterface );
		generateForward( cw, PREFIX_WRITE_METHOD, "(ILjava/lang/Object;Ljava/lang/Object;)V", hostName, hostIsInterface );
		generateForward( cw, PREFIX_CREATE_METHOD, "(I[Ljava/lang/Object;)Ljava/lang/Object;", hostName, hostIsInterface );

		// Expose identity without requiring the factory to access a non-public host type.
		MethodVisitor identity = cw.visitMethod( ACC_PUBLIC | ACC_STATIC | ACC_SYNTHETIC,
				METHOD_NAME_HOST_CLASS, "()Ljava/lang/Class;", null, null );
		identity.visitCode();
		identity.visitLdcInsn( Type.getObjectType( hostName ) );
		identity.visitInsn( ARETURN );
		identity.visitMaxs( 1, 0 );
		identity.visitEnd();

		// Always include all accessor method forwards for bridge
		generateForward( cw, METHOD_NAME_FIELD_READER_ACCESSOR, accessorMethodDescriptor( READER_INTERFACE_INTERNAL ), hostName,
				hostIsInterface );
		generateForward( cw, METHOD_NAME_METHOD_READER_ACCESSOR, accessorMethodDescriptor( READER_INTERFACE_INTERNAL ), hostName,
				hostIsInterface );
		generateForward( cw, METHOD_NAME_FIELD_WRITER_ACCESSOR, accessorMethodDescriptor( WRITER_INTERFACE_INTERNAL ), hostName,
				hostIsInterface );
		generateForward( cw, METHOD_NAME_METHOD_WRITER_ACCESSOR, accessorMethodDescriptor( WRITER_INTERFACE_INTERNAL ), hostName,
				hostIsInterface );
		generateForward( cw, METHOD_NAME_INSTANTIATOR_ACCESSOR, accessorMethodDescriptor( INSTANTIATOR_INTERFACE_INTERNAL ),
				hostName, hostIsInterface );

		cw.visitEnd();
		return cw.toByteArray();
	}

	private static String accessorMethodDescriptor(String name) {
		return "(Ljava/lang/String;)L" + name + ";";
	}

	// Assuming "Model" is a class from the application model (e.g. an entity), here are
	// example methods generated in Model$$HibernateAccessorBridge, in the same package as Model:
	// public static Object $$__hibernateAccessor_read(int memberIndex, Object target) {
	//     return Model.$$__hibernateAccessor_read(memberIndex, target);
	// }
	// public static void $$__hibernateAccessor_write(int memberIndex, Object target, Object value) {
	//     Model.$$__hibernateAccessor_write(memberIndex, target, value);
	// }
	// Creation and accessor lookup methods similarly forward all arguments and return the host's result.
	private static void generateForward(ClassWriter cw, String methodName, String descriptor,
			String hostName, boolean hostIsInterface) {
		MethodVisitor mv = cw.visitMethod( ACC_PUBLIC | ACC_STATIC | ACC_SYNTHETIC, methodName, descriptor, null, null );
		mv.visitCode();

		Type[] argTypes = Type.getArgumentTypes( descriptor );
		for ( int i = 0, slot = 0; i < argTypes.length; i++ ) {
			mv.visitVarInsn( argTypes[i].getOpcode( ILOAD ), slot );
			slot += argTypes[i].getSize();
		}

		mv.visitMethodInsn( INVOKESTATIC, hostName, methodName, descriptor, hostIsInterface );

		Type returnType = Type.getReturnType( descriptor );
		mv.visitInsn( returnType.getOpcode( IRETURN ) );

		mv.visitMaxs( argTypes.length, argTypes.length );
		mv.visitEnd();
	}
}
