package org.hibernate.accessor.asm.aot;

import static org.hibernate.accessor.asm.aot.GenerationUtil.emitStringSwitch;
import static org.hibernate.accessor.asm.aot.GenerationUtil.pushIntConst;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.FACTORY_INTERFACE_INTERNAL;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.GENERATED_CLASS_VERSION;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.INSTANTIATOR_INTERFACE_INTERNAL;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.METHOD_NAME_FIELD_READER_ACCESSOR;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.METHOD_NAME_FIELD_WRITER_ACCESSOR;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.METHOD_NAME_HOST_CLASS;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.METHOD_NAME_INSTANTIATOR_ACCESSOR;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.METHOD_NAME_METHOD_READER_ACCESSOR;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.METHOD_NAME_METHOD_WRITER_ACCESSOR;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.MULTI_VALUE_GENERATION_EXCEPTION_INTERNAL;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.MULTI_VALUE_READER_INTERFACE_INTERNAL;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.MULTI_VALUE_WRITER_INTERFACE_INTERNAL;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.READER_INTERFACE_INTERNAL;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.WRITER_INTERFACE_INTERNAL;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Generates the accessor factory that resolves class and member names to shared accessor implementation instances.
 * Name matches are checked against declaring-class identity before selecting an AOT accessor.
 * The caller installs generated classes and transforms hosts before loading them.
 */
class FactoryGenerator implements Opcodes {

	private final String FACTORY_IMPLEMENTATION_INTERNAL;
	private final int stringChunkSize;

	FactoryGenerator(GenerationNames names, int stringChunkSize) {
		this.stringChunkSize = stringChunkSize;
		FACTORY_IMPLEMENTATION_INTERNAL = names.factory().replace( '.', '/' );
	}

	private final Map<String, String> dispatchTargets = new LinkedHashMap<>();
	private final Set<String> interfaceTargets = new HashSet<>();

	private final Set<String> fieldReaderClasses = new LinkedHashSet<>();
	private final Set<String> methodReaderClasses = new LinkedHashSet<>();
	private final Set<String> fieldWriterClasses = new LinkedHashSet<>();
	private final Set<String> methodWriterClasses = new LinkedHashSet<>();
	private final Set<String> instantiatorClasses = new LinkedHashSet<>();

	void registerDispatchTarget(String declaringClassFqcn, String dispatchTargetInternal, boolean isInterface) {
		dispatchTargets.put( declaringClassFqcn, dispatchTargetInternal );
		if ( isInterface ) {
			interfaceTargets.add( dispatchTargetInternal );
		}
	}

	void registerFieldReader(String declaringClassFqcn) {
		fieldReaderClasses.add( declaringClassFqcn );
	}

	void registerMethodReader(String declaringClassFqcn) {
		methodReaderClasses.add( declaringClassFqcn );
	}

	void registerFieldWriter(String declaringClassFqcn) {
		fieldWriterClasses.add( declaringClassFqcn );
	}

	void registerMethodWriter(String declaringClassFqcn) {
		methodWriterClasses.add( declaringClassFqcn );
	}

	void registerInstantiator(String declaringClassFqcn) {
		instantiatorClasses.add( declaringClassFqcn );
	}

	byte[] generate(boolean withFallback) {
		ClassWriter cw = new ClassWriter( ClassWriter.COMPUTE_FRAMES );

		cw.visit( GENERATED_CLASS_VERSION, ACC_PUBLIC | ACC_SUPER, FACTORY_IMPLEMENTATION_INTERNAL, null,
				"java/lang/Object", new String[] { FACTORY_INTERFACE_INTERNAL } );

		// Static singleton reference initialized by create().
		cw.visitField( ACC_PRIVATE | ACC_STATIC | ACC_VOLATILE, "INSTANCE", "L" + FACTORY_INTERFACE_INTERNAL + ";", null, null )
				.visitEnd();

		if ( withFallback ) {
			cw.visitField( ACC_PRIVATE | ACC_FINAL, "fallback", "L" + FACTORY_INTERFACE_INTERNAL + ";", null, null ).visitEnd();
		}

		generateKeyHelpers( cw );
		generateConstructor( cw, withFallback );
		generateCreateMethod( cw, withFallback );

		generateValueAccessor( cw, withFallback, "valueReader", "java/lang/reflect/Field",
				METHOD_NAME_FIELD_READER_ACCESSOR, READER_INTERFACE_INTERNAL, fieldReaderClasses );
		generateValueAccessor( cw, withFallback, "valueReader", "java/lang/reflect/Method",
				METHOD_NAME_METHOD_READER_ACCESSOR, READER_INTERFACE_INTERNAL, methodReaderClasses );
		generateValueAccessor( cw, withFallback, "valueWriter", "java/lang/reflect/Field",
				METHOD_NAME_FIELD_WRITER_ACCESSOR, WRITER_INTERFACE_INTERNAL, fieldWriterClasses );
		generateValueAccessor( cw, withFallback, "valueWriter", "java/lang/reflect/Method",
				METHOD_NAME_METHOD_WRITER_ACCESSOR, WRITER_INTERFACE_INTERNAL, methodWriterClasses );
		generateInstantiatorMethod( cw, withFallback );
		generateMultiValueAccessor( cw, "multiValueReader", MULTI_VALUE_READER_INTERFACE_INTERNAL );
		generateMultiValueAccessor( cw, "multiValueWriter", MULTI_VALUE_WRITER_INTERFACE_INTERNAL );

		cw.visitEnd();
		return cw.toByteArray();
	}

	// These helpers are emitted into the factory so no generator utility is needed at runtime.
	private static void generateKeyHelpers(ClassWriter cw) {
		MethodVisitor mv = cw.visitMethod( ACC_PRIVATE | ACC_STATIC, "memberKey",
				"(Ljava/lang/reflect/Member;)Ljava/lang/String;", null, null );
		mv.visitCode();
		mv.visitVarInsn( ALOAD, 0 );
		mv.visitMethodInsn( INVOKEINTERFACE, "java/lang/reflect/Member", "getName", "()Ljava/lang/String;", true );
		mv.visitVarInsn( ASTORE, 1 );
		mv.visitVarInsn( ALOAD, 0 );
		mv.visitTypeInsn( INSTANCEOF, "java/lang/reflect/Method" );
		Label field = new Label();
		mv.visitJumpInsn( IFEQ, field );
		mv.visitVarInsn( ALOAD, 0 );
		mv.visitTypeInsn( CHECKCAST, "java/lang/reflect/Method" );
		mv.visitVarInsn( ASTORE, 2 );
		mv.visitVarInsn( ALOAD, 1 );
		mv.visitVarInsn( ALOAD, 2 );
		mv.visitMethodInsn( INVOKEVIRTUAL, "java/lang/reflect/Method", "getReturnType", "()Ljava/lang/Class;", false );
		mv.visitVarInsn( ALOAD, 2 );
		mv.visitMethodInsn( INVOKEVIRTUAL, "java/lang/reflect/Method", "getParameterTypes", "()[Ljava/lang/Class;", false );
		emitDescriptor( mv );
		mv.visitMethodInsn( INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false );
		mv.visitInsn( ARETURN );
		mv.visitLabel( field );
		mv.visitVarInsn( ALOAD, 1 );
		mv.visitInsn( ARETURN );
		mv.visitMaxs( 0, 0 );
		mv.visitEnd();

		mv = cw.visitMethod( ACC_PRIVATE | ACC_STATIC, "constructorDescriptor",
				"(Ljava/lang/reflect/Constructor;)Ljava/lang/String;", null, null );
		mv.visitCode();
		mv.visitFieldInsn( GETSTATIC, "java/lang/Void", "TYPE", "Ljava/lang/Class;" );
		mv.visitVarInsn( ALOAD, 0 );
		mv.visitMethodInsn( INVOKEVIRTUAL, "java/lang/reflect/Constructor", "getParameterTypes", "()[Ljava/lang/Class;", false );
		emitDescriptor( mv );
		mv.visitInsn( ARETURN );
		mv.visitMaxs( 0, 0 );
		mv.visitEnd();
	}

	private static void emitDescriptor(MethodVisitor mv) {
		mv.visitMethodInsn( INVOKESTATIC, "java/lang/invoke/MethodType", "methodType",
				"(Ljava/lang/Class;[Ljava/lang/Class;)Ljava/lang/invoke/MethodType;", false );
		mv.visitMethodInsn( INVOKEVIRTUAL, "java/lang/invoke/MethodType", "toMethodDescriptorString", "()Ljava/lang/String;", false );
	}

	/**
	 * Multi-value (bulk) accessors are not generated at build time yet.
	 * Throwing {@link org.hibernate.accessor.MultiValueAccessorGenerationException} is the contract the accessor
	 * library defines for consumers to fall back to per-property access.
	 * The Quarkus ORM integration wraps this factory and returns {@code null} for bulk accessors without calling
	 * these methods, so normal ORM bootstrap does not use these exceptions for control flow. Direct callers
	 * still receive the exception required by the accessor contract; that contract does not define a nullable result.
	 *
	 * <pre>
	 * public MultiValueReader multiValueReader(Class type, Member... members) {
	 *     throw new MultiValueAccessorGenerationException(
	 *             "Multi-value accessors are not generated at build time by this AOT accessor factory");
	 * }
	 * </pre>
	 *
	 * The writer overload has the same body and returns {@code MultiValueWriter}.
	 */
	private static void generateMultiValueAccessor(ClassWriter cw, String factoryMethodName, String returnInterface) {
		MethodVisitor mv = cw.visitMethod( ACC_PUBLIC | ACC_VARARGS, factoryMethodName,
				"(Ljava/lang/Class;[Ljava/lang/reflect/Member;)L" + returnInterface + ";", null, null );
		mv.visitCode();
		mv.visitTypeInsn( NEW, MULTI_VALUE_GENERATION_EXCEPTION_INTERNAL );
		mv.visitInsn( DUP );
		mv.visitLdcInsn( "Multi-value accessors are not generated at build time by this AOT accessor factory" );
		mv.visitMethodInsn( INVOKESPECIAL, MULTI_VALUE_GENERATION_EXCEPTION_INTERNAL,
				"<init>", "(Ljava/lang/String;)V", false );
		mv.visitInsn( ATHROW );
		mv.visitMaxs( 0, 0 );
		mv.visitEnd();
	}

	// Example generated code (names, types and indexes depend on the registered members):
	// private QuarkusHibernateAccessorFactory(AccessorFactory fallback) {
	//     super();
	//     this.fallback = fallback;
	// }
	// Without fallback, generate a private no-argument constructor containing only super().
	private void generateConstructor(ClassWriter cw, boolean withFallback) {
		String desc = withFallback
				? "(L" + FACTORY_INTERFACE_INTERNAL + ";)V"
				: "()V";
		MethodVisitor mv = cw.visitMethod( ACC_PRIVATE, "<init>", desc, null, null );
		mv.visitCode();
		mv.visitVarInsn( ALOAD, 0 );
		mv.visitMethodInsn( INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false );
		if ( withFallback ) {
			mv.visitVarInsn( ALOAD, 0 );
			mv.visitVarInsn( ALOAD, 1 );
			mv.visitFieldInsn( PUTFIELD, FACTORY_IMPLEMENTATION_INTERNAL, "fallback",
					"L" + FACTORY_INTERFACE_INTERNAL + ";" );
		}
		mv.visitInsn( RETURN );
		mv.visitMaxs( 0, 0 );
		mv.visitEnd();
	}

	// Example generated code (names, types and indexes depend on the registered members):
	// public static AccessorFactory create(AccessorFactory fallback) {
	//     if (INSTANCE == null) {
	//         synchronized (QuarkusHibernateAccessorFactory.class) {
	//             if (INSTANCE == null) {
	//                 INSTANCE = new QuarkusHibernateAccessorFactory(fallback);
	//             }
	//         }
	//     }
	//     return INSTANCE;
	// }
	// Without fallback, both create() and the constructor take no arguments.
	// The catch-all handler emits the monitor release required when the synchronized block throws.
	//
	// First call wins: INSTANCE is created once, from the fallback passed to whichever call
	// observes it as null. Every later call returns that same instance and silently discards its
	// own fallback argument, which is never compared against the one already captured. There is no
	// reset, so callers that need a particular fallback must install it before anything else can
	// request the factory.
	private void generateCreateMethod(ClassWriter cw, boolean withFallback) {
		String factoryDesc = "L" + FACTORY_INTERFACE_INTERNAL + ";";
		String implDesc = "L" + FACTORY_IMPLEMENTATION_INTERNAL + ";";
		String createDesc = withFallback
				? "(" + factoryDesc + ")" + factoryDesc
				: "()" + factoryDesc;
		String ctorDesc = withFallback
				? "(" + factoryDesc + ")V"
				: "()V";

		MethodVisitor mv = cw.visitMethod( ACC_PUBLIC | ACC_STATIC, "create", createDesc, null, null );
		mv.visitCode();

		// First null check (no lock)
		mv.visitFieldInsn( GETSTATIC, FACTORY_IMPLEMENTATION_INTERNAL, "INSTANCE", factoryDesc );
		Label notNull1 = new Label();
		mv.visitJumpInsn( IFNONNULL, notNull1 );

		// synchronized (QuarkusHibernateAccessorFactory.class)
		mv.visitLdcInsn( Type.getObjectType( FACTORY_IMPLEMENTATION_INTERNAL ) );
		mv.visitInsn( DUP );
		int monitorSlot = withFallback ? 1 : 0;
		mv.visitVarInsn( ASTORE, monitorSlot );
		mv.visitInsn( MONITORENTER );

		Label tryStart = new Label();
		Label tryEnd = new Label();
		Label catchLabel = new Label();
		mv.visitTryCatchBlock( tryStart, tryEnd, catchLabel, null );

		mv.visitLabel( tryStart );

		// Second null check (under lock)
		mv.visitFieldInsn( GETSTATIC, FACTORY_IMPLEMENTATION_INTERNAL, "INSTANCE", factoryDesc );
		Label notNull2 = new Label();
		mv.visitJumpInsn( IFNONNULL, notNull2 );

		// INSTANCE = new QuarkusHibernateAccessorFactory(fallback)
		mv.visitTypeInsn( NEW, FACTORY_IMPLEMENTATION_INTERNAL );
		mv.visitInsn( DUP );
		if ( withFallback ) {
			mv.visitVarInsn( ALOAD, 0 );
		}
		mv.visitMethodInsn( INVOKESPECIAL, FACTORY_IMPLEMENTATION_INTERNAL, "<init>", ctorDesc, false );
		mv.visitFieldInsn( PUTSTATIC, FACTORY_IMPLEMENTATION_INTERNAL, "INSTANCE", factoryDesc );

		mv.visitLabel( notNull2 );
		mv.visitFrame( F_FULL,
				withFallback ? 2 : 1,
				withFallback
						? new Object[] { FACTORY_INTERFACE_INTERNAL, "java/lang/Object" }
						: new Object[] { "java/lang/Object" },
				0, null );
		mv.visitVarInsn( ALOAD, monitorSlot );
		mv.visitInsn( MONITOREXIT );

		mv.visitLabel( tryEnd );
		Label afterSync = new Label();
		mv.visitJumpInsn( GOTO, afterSync );

		// catch-all: monitorexit + rethrow
		mv.visitLabel( catchLabel );
		mv.visitFrame( F_FULL,
				withFallback ? 2 : 1,
				withFallback
						? new Object[] { FACTORY_INTERFACE_INTERNAL, "java/lang/Object" }
						: new Object[] { "java/lang/Object" },
				1, new Object[] { "java/lang/Throwable" } );
		mv.visitVarInsn( ALOAD, monitorSlot );
		mv.visitInsn( MONITOREXIT );
		mv.visitInsn( ATHROW );

		mv.visitLabel( afterSync );
		mv.visitFrame( F_FULL,
				withFallback ? 2 : 1,
				withFallback
						? new Object[] { FACTORY_INTERFACE_INTERNAL, "java/lang/Object" }
						: new Object[] { "java/lang/Object" },
				0, null );

		mv.visitLabel( notNull1 );
		mv.visitFrame( F_FULL,
				withFallback ? 1 : 0,
				withFallback
						? new Object[] { FACTORY_INTERFACE_INTERNAL }
						: new Object[] { },
				0, null );
		mv.visitFieldInsn( GETSTATIC, FACTORY_IMPLEMENTATION_INTERNAL, "INSTANCE", factoryDesc );
		mv.visitInsn( ARETURN );

		mv.visitMaxs( 0, 0 );
		mv.visitEnd();
	}

	// Example generated code (names, types and indexes depend on the registered members):
	// public ValueReader valueReader(Field member) {
	//     String className = member.getDeclaringClass().getName();
	//     String key = NamingUtil.memberKey(member);
	//     switch (className) {
	//         case "example.Model": {
	//             if (member.getDeclaringClass() != Model.class) break;
	//             ValueReader result = Model.$$__hibernateAccessor_fieldReader(key);
	//             if (result != null) return result;
	//             break;
	//         }
	//         // More registered hosts...
	//     }
	//     return fallback.valueReader(member);
	//     // Without fallback: throw new UnsupportedOperationException(className + "." + key);
	// }
	// Method readers and field/method writers use the corresponding host lookup and factory overload.
	private void generateValueAccessor(ClassWriter cw, boolean withFallback, String factoryMethodName,
			String reflectType,
			String hostMethodName, String returnInterface,
			Set<String> classes) {
		String returnDesc = "L" + returnInterface + ";";
		String hostMethodDesc = "(Ljava/lang/String;)" + returnDesc;

		MethodVisitor mv = cw.visitMethod( ACC_PUBLIC, factoryMethodName,
				"(L" + reflectType + ";)L" + returnInterface + ";", null, null );
		mv.visitCode();

		// Use the same core validation as the runtime factories, before lookup or fallback.
		mv.visitVarInsn( ALOAD, 1 );
		boolean field = reflectType.equals( "java/lang/reflect/Field" );
		mv.visitMethodInsn( INVOKESTATIC, "org/hibernate/accessor/spi/MemberValidation",
				field ? "validateInstanceMember" : factoryMethodName.equals( "valueReader" ) ? "validateReaderMethod" : "validateWriterMethod",
				field ? "(Ljava/lang/reflect/Member;)V" : "(Ljava/lang/reflect/Method;)V", false );

		// slot 0 = this, slot 1 = field/method arg
		// Extract className -> slot 2
		mv.visitVarInsn( ALOAD, 1 );
		mv.visitMethodInsn( INVOKEVIRTUAL, reflectType, "getDeclaringClass",
				"()Ljava/lang/Class;", false );
		mv.visitMethodInsn( INVOKEVIRTUAL, "java/lang/Class", "getName",
				"()Ljava/lang/String;", false );
		mv.visitVarInsn( ASTORE, 2 );

		// Extract member key -> slot 3
		mv.visitVarInsn( ALOAD, 1 );
		mv.visitMethodInsn( INVOKESTATIC, FACTORY_IMPLEMENTATION_INTERNAL, "memberKey",
				"(Ljava/lang/reflect/Member;)Ljava/lang/String;", false );
		mv.visitVarInsn( ASTORE, 3 );

		if ( classes.isEmpty() ) {
			emitThrowOrFallback( mv, withFallback, factoryMethodName, reflectType, returnInterface );
			mv.visitMaxs( 0, 0 );
			mv.visitEnd();
			return;
		}

		List<String> classNames = new ArrayList<>( classes );
		Label throwLabel = new Label();

		if ( classNames.size() <= stringChunkSize ) {
			generateValueAccessorSwitch( mv, classNames, hostMethodName, hostMethodDesc,
					returnInterface, throwLabel );
		}
		else {
			generateValueAccessorChunked( cw, mv, factoryMethodName + "_" + hostMethodName,
					classNames, hostMethodName, hostMethodDesc, returnInterface, throwLabel );
		}

		// After switch — no class match or null result
		mv.visitLabel( throwLabel );
		mv.visitFrame( F_FULL, 4,
				new Object[] {
						FACTORY_IMPLEMENTATION_INTERNAL,
						reflectType,
						"java/lang/String",
						"java/lang/String" },
				0, null );
		emitThrowOrFallback( mv, withFallback, factoryMethodName, reflectType, returnInterface );

		mv.visitMaxs( 0, 0 );
		mv.visitEnd();
	}

	// Example generated class lookup block, using locals className (slot 2) and key (slot 3):
	// switch (className) {
	//     case "example.Model": {
	//         if (member.getDeclaringClass() != Model.class) break;
	//         ValueReader result = Model.$$__hibernateAccessor_fieldReader(key);
	//         if (result != null) return result;
	//         // Jump to the caller's throw/fallback block.
	//         break;
	//     }
	//     // Unmatched class names also jump to that block.
	// }
	private void generateValueAccessorSwitch(MethodVisitor mv, List<String> classNames,
			String hostMethodName, String hostMethodDesc,
			String implInternal, Label throwLabel) {
		Label defaultLabel = new Label();

		emitStringSwitch( mv, 2, 4, classNames, defaultLabel, (caseMv, classIdx) -> {
			String className = classNames.get( classIdx );
			String target = dispatchTargets.get( className );
			boolean isIface = interfaceTargets.contains( target );

			emitDeclaringClassCheck( caseMv, className, 1, throwLabel );

			caseMv.visitVarInsn( ALOAD, 3 );
			caseMv.visitMethodInsn( INVOKESTATIC, target, hostMethodName,
					hostMethodDesc, isIface );
			caseMv.visitInsn( DUP );
			Label notNull = new Label();
			caseMv.visitJumpInsn( IFNONNULL, notNull );
			caseMv.visitInsn( POP );
			caseMv.visitJumpInsn( GOTO, throwLabel );
			caseMv.visitLabel( notNull );
			caseMv.visitInsn( ARETURN );
		} );

		// Default of string switch — no class match
		mv.visitLabel( defaultLabel );
		mv.visitFrame( F_SAME, 0, null, 0, null );
		mv.visitJumpInsn( GOTO, throwLabel );
	}

	// Generated dispatch shape; lookup is a placeholder helper name and numChunks is a build-time constant:
	// ValueReader result;
	// switch ((className.hashCode() & 0x7FFFFFFF) % numChunks) {
	//     case 0: result = lookup$0(className, key, member); break;
	//     case 1: result = lookup$1(className, key, member); break;
	//     default: /* jump to throw/fallback block */
	// }
	// if (result != null) return result;
	// Otherwise jump to the caller's throw/fallback block; empty buckets do so directly.
	private void generateValueAccessorChunked(ClassWriter cw, MethodVisitor mv,
			String chunkBaseName,
			List<String> classNames, String hostMethodName, String hostMethodDesc,
			String implInternal, Label throwLabel) {
		int numChunks = ( classNames.size() - 1 ) / stringChunkSize + 1;

		List<List<String>> chunks = new ArrayList<>();
		for ( int i = 0; i < numChunks; i++ ) {
			chunks.add( new ArrayList<>() );
		}
		// Use the same hash partition in the generated dispatcher so each request visits only one bucket.
		// Equality checks in bounded helpers still distinguish names with identical hashes.
		for ( String className : classNames ) {
			int bucket = ( className.hashCode() & 0x7FFFFFFF ) % numChunks;
			chunks.get( bucket ).add( className );
		}

		String chunkMethodDesc = "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/reflect/Member;)" + "L" + implInternal + ";";

		for ( int i = 0; i < numChunks; i++ ) {
			if ( !chunks.get( i ).isEmpty() ) {
				generateValueAccessorChunkMethod( cw, chunkBaseName + "$" + i,
						chunkMethodDesc, chunks.get( i ), hostMethodName, hostMethodDesc, implInternal );
			}
		}

		// Dispatcher in the main method
		Label defaultLabel = new Label();
		Label[] labels = new Label[numChunks];
		for ( int i = 0; i < numChunks; i++ ) {
			labels[i] = chunks.get( i ).isEmpty() ? defaultLabel : new Label();
		}

		mv.visitVarInsn( ALOAD, 2 );
		mv.visitMethodInsn( INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false );
		mv.visitLdcInsn( 0x7FFFFFFF );
		mv.visitInsn( IAND );
		pushIntConst( mv, numChunks );
		mv.visitInsn( IREM );
		mv.visitTableSwitchInsn( 0, numChunks - 1, defaultLabel, labels );

		for ( int i = 0; i < numChunks; i++ ) {
			if ( !chunks.get( i ).isEmpty() ) {
				mv.visitLabel( labels[i] );
				mv.visitFrame( F_SAME, 0, null, 0, null );
				mv.visitVarInsn( ALOAD, 2 );
				mv.visitVarInsn( ALOAD, 3 );
				mv.visitVarInsn( ALOAD, 1 );
				mv.visitMethodInsn( INVOKESTATIC, FACTORY_IMPLEMENTATION_INTERNAL, chunkBaseName + "$" + i,
						chunkMethodDesc, false );
				// Check for null result
				mv.visitInsn( DUP );
				Label notNull = new Label();
				mv.visitJumpInsn( IFNONNULL, notNull );
				mv.visitInsn( POP );
				mv.visitJumpInsn( GOTO, throwLabel );
				mv.visitLabel( notNull );
				mv.visitInsn( ARETURN );
			}
		}

		mv.visitLabel( defaultLabel );
		mv.visitFrame( F_SAME, 0, null, 0, null );
		mv.visitJumpInsn( GOTO, throwLabel );
	}

	// Example generated chunk helper (lookup stands for the supplied methodName):
	// private static ValueReader lookup$0(String className, String key, Member member) {
	//     switch (className) {
	//         case "example.Model":
	//             return member.getDeclaringClass() == Model.class
	//                     ? Model.$$__hibernateAccessor_fieldReader(key) : null;
	//         default: return null;
	//     }
	// }
	private void generateValueAccessorChunkMethod(ClassWriter cw, String methodName,
			String methodDesc, List<String> classNames,
			String hostMethodName, String hostMethodDesc, String implInternal) {
		// A hash bucket may contain every registered class. Bound each generated method by count.
		for ( int start = 0, part = 0; start < classNames.size(); start += stringChunkSize, part++ ) {
			int end = Math.min( start + stringChunkSize, classNames.size() );
			String current = part == 0 ? methodName : methodName + "$part" + part;
			String next = end == classNames.size() ? null : methodName + "$part" + ( part + 1 );
			generateValueAccessorChunkPart( cw, current, methodDesc, classNames.subList( start, end ),
					hostMethodName, hostMethodDesc, next );
		}
	}

	private void generateValueAccessorChunkPart(ClassWriter cw, String methodName,
			String methodDesc, List<String> classNames,
			String hostMethodName, String hostMethodDesc, String next) {
		MethodVisitor mv = cw.visitMethod( ACC_PRIVATE | ACC_STATIC, methodName,
				methodDesc, null, null );
		mv.visitCode();

		Label defaultLabel = new Label();

		// slot 0 = className, slot 1 = memberName, slot 2 = reflected member
		Label mismatch = new Label();
		emitStringSwitch( mv, 0, 3, classNames, defaultLabel, (caseMv, classIdx) -> {
			String className = classNames.get( classIdx );
			String target = dispatchTargets.get( className );
			boolean isIface = interfaceTargets.contains( target );

			emitDeclaringClassCheck( caseMv, className, 2, mismatch );

			caseMv.visitVarInsn( ALOAD, 1 );
			caseMv.visitMethodInsn( INVOKESTATIC, target, hostMethodName,
					hostMethodDesc, isIface );
			caseMv.visitInsn( ARETURN );
		} );

		mv.visitLabel( defaultLabel );
		mv.visitFrame( F_SAME, 0, null, 0, null );
		if ( next != null ) {
			mv.visitVarInsn( ALOAD, 0 );
			mv.visitVarInsn( ALOAD, 1 );
			mv.visitVarInsn( ALOAD, 2 );
			mv.visitMethodInsn( INVOKESTATIC, FACTORY_IMPLEMENTATION_INTERNAL, next, methodDesc, false );
		}
		else {
			mv.visitInsn( ACONST_NULL );
		}
		mv.visitInsn( ARETURN );
		mv.visitLabel( mismatch );
		mv.visitInsn( ACONST_NULL );
		mv.visitInsn( ARETURN );

		mv.visitMaxs( 0, 0 );
		mv.visitEnd();
	}

	// Example generated code (names, types and indexes depend on the registered members):
	// public Instantiator instantiator(Constructor constructor) {
	//     String className = constructor.getDeclaringClass().getName();
	//     String key = NamingUtil.constructorDescriptor(constructor);
	//     switch (className) {
	//         case "example.Model": {
	//             if (constructor.getDeclaringClass() != Model.class) break;
	//             Instantiator result = Model.$$__hibernateAccessor_instantiator(key);
	//             if (result != null) return result;
	//             break;
	//         }
	//     }
	//     return fallback.instantiator(constructor);
	//     // Without fallback: throw new UnsupportedOperationException(className + "." + key);
	// }
	private void generateInstantiatorMethod(ClassWriter cw, boolean withFallback) {
		String hostMethodDesc = "(Ljava/lang/String;)L" + INSTANTIATOR_INTERFACE_INTERNAL + ";";

		MethodVisitor mv = cw.visitMethod( ACC_PUBLIC, "instantiator",
				"(Ljava/lang/reflect/Constructor;)L" + INSTANTIATOR_INTERFACE_INTERNAL + ";",
				null, null );
		mv.visitCode();

		// Extract className -> slot 2
		mv.visitVarInsn( ALOAD, 1 );
		mv.visitMethodInsn( INVOKEVIRTUAL, "java/lang/reflect/Constructor", "getDeclaringClass",
				"()Ljava/lang/Class;", false );
		mv.visitMethodInsn( INVOKEVIRTUAL, "java/lang/Class", "getName",
				"()Ljava/lang/String;", false );
		mv.visitVarInsn( ASTORE, 2 );

		// Extract constructor descriptor -> slot 3
		mv.visitVarInsn( ALOAD, 1 );
		mv.visitMethodInsn( INVOKESTATIC, FACTORY_IMPLEMENTATION_INTERNAL, "constructorDescriptor",
				"(Ljava/lang/reflect/Constructor;)Ljava/lang/String;", false );
		mv.visitVarInsn( ASTORE, 3 );

		if ( instantiatorClasses.isEmpty() ) {
			emitThrowOrFallback( mv, withFallback, "instantiator", "java/lang/reflect/Constructor",
					INSTANTIATOR_INTERFACE_INTERNAL );
			mv.visitMaxs( 0, 0 );
			mv.visitEnd();
			return;
		}

		List<String> classNames = new ArrayList<>( instantiatorClasses );
		Label throwLabel = new Label();

		if ( classNames.size() <= stringChunkSize ) {
			Label defaultLabel = new Label();

			emitStringSwitch( mv, 2, 4, classNames, defaultLabel, (caseMv, classIdx) -> {
				String className = classNames.get( classIdx );
				String target = dispatchTargets.get( className );
				boolean isIface = interfaceTargets.contains( target );

				emitDeclaringClassCheck( caseMv, className, 1, throwLabel );

				caseMv.visitVarInsn( ALOAD, 3 );
				caseMv.visitMethodInsn( INVOKESTATIC, target, METHOD_NAME_INSTANTIATOR_ACCESSOR,
						hostMethodDesc, isIface );
				caseMv.visitInsn( DUP );
				Label notNull = new Label();
				caseMv.visitJumpInsn( IFNONNULL, notNull );
				caseMv.visitInsn( POP );
				caseMv.visitJumpInsn( GOTO, throwLabel );
				caseMv.visitLabel( notNull );
				caseMv.visitInsn( ARETURN );
			} );

			mv.visitLabel( defaultLabel );
			mv.visitFrame( F_SAME, 0, null, 0, null );
			mv.visitJumpInsn( GOTO, throwLabel );
		}
		else {
			// Chunked dispatch for instantiator (same pattern as value accessor)
			generateValueAccessorChunked( cw, mv, "instantiator_" + METHOD_NAME_INSTANTIATOR_ACCESSOR,
					classNames, METHOD_NAME_INSTANTIATOR_ACCESSOR, hostMethodDesc,
					INSTANTIATOR_INTERFACE_INTERNAL, throwLabel );
		}

		mv.visitLabel( throwLabel );
		mv.visitFrame( F_FULL, 4,
				new Object[] {
						FACTORY_IMPLEMENTATION_INTERNAL,
						"java/lang/reflect/Constructor",
						"java/lang/String",
						"java/lang/String" },
				0, null );
		emitThrowOrFallback( mv, withFallback, "instantiator", "java/lang/reflect/Constructor", INSTANTIATOR_INTERFACE_INTERNAL );

		mv.visitMaxs( 0, 0 );
		mv.visitEnd();
	}

	private void emitDeclaringClassCheck(MethodVisitor mv, String className, int memberSlot, Label mismatch) {
		mv.visitVarInsn( ALOAD, memberSlot );
		mv.visitMethodInsn( INVOKEINTERFACE, "java/lang/reflect/Member", "getDeclaringClass", "()Ljava/lang/Class;", true );
		String host = className.replace( '.', '/' );
		String target = dispatchTargets.get( className );
		if ( host.equals( target ) ) {
			mv.visitLdcInsn( Type.getObjectType( host ) );
		}
		else {
			mv.visitMethodInsn( INVOKESTATIC, target, METHOD_NAME_HOST_CLASS, "()Ljava/lang/Class;", false );
		}
		mv.visitJumpInsn( IF_ACMPNE, mismatch );
	}

	// Example generated terminal block; the factory method and member type vary by accessor kind:
	// return this.fallback.valueReader(member);
	// Or, without fallback:
	// throw new UnsupportedOperationException(className + "." + key);
	private void emitThrowOrFallback(MethodVisitor mv, boolean withFallback, String factoryMethodName,
			String reflectType, String returnInterface) {
		if ( withFallback ) {
			mv.visitVarInsn( ALOAD, 0 );
			mv.visitFieldInsn( GETFIELD, FACTORY_IMPLEMENTATION_INTERNAL, "fallback", "L" + FACTORY_INTERFACE_INTERNAL + ";" );
			mv.visitVarInsn( ALOAD, 1 );
			mv.visitMethodInsn( INVOKEINTERFACE, FACTORY_INTERFACE_INTERNAL, factoryMethodName,
					"(L" + reflectType + ";)L" + returnInterface + ";", true );
			mv.visitInsn( ARETURN );
		}
		else {
			emitThrow( mv );
		}
	}

	// Generated failure expression; className and key are locals in slots 2 and 3:
	// throw new UnsupportedOperationException(className + "." + key);
	private static void emitThrow(MethodVisitor mv) {
		mv.visitTypeInsn( NEW, "java/lang/UnsupportedOperationException" );
		mv.visitInsn( DUP );
		mv.visitVarInsn( ALOAD, 2 );
		mv.visitLdcInsn( "." );
		mv.visitMethodInsn( INVOKEVIRTUAL, "java/lang/String", "concat",
				"(Ljava/lang/String;)Ljava/lang/String;", false );
		mv.visitVarInsn( ALOAD, 3 );
		mv.visitMethodInsn( INVOKEVIRTUAL, "java/lang/String", "concat",
				"(Ljava/lang/String;)Ljava/lang/String;", false );
		mv.visitMethodInsn( INVOKESPECIAL, "java/lang/UnsupportedOperationException",
				"<init>", "(Ljava/lang/String;)V", false );
		mv.visitInsn( ATHROW );
	}
}
