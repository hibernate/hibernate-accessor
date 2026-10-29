package org.hibernate.accessor.asm.aot;

import static org.hibernate.accessor.asm.aot.GenerationUtil.emitStringSwitch;
import static org.hibernate.accessor.asm.aot.GenerationUtil.fqcnToName;
import static org.hibernate.accessor.asm.aot.GenerationUtil.pushIntConst;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.INSTANTIATOR_INTERFACE_INTERNAL;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.METHOD_NAME_FIELD_READER_ACCESSOR;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.METHOD_NAME_FIELD_WRITER_ACCESSOR;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.METHOD_NAME_INSTANTIATOR_ACCESSOR;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.METHOD_NAME_METHOD_READER_ACCESSOR;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.METHOD_NAME_METHOD_WRITER_ACCESSOR;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.PREFIX_CREATE_METHOD;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.PREFIX_READ_METHOD;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.PREFIX_WRITE_METHOD;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.READER_INTERFACE_INTERNAL;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.WRITER_INTERFACE_INTERNAL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.hibernate.accessor.asm.impl.BytecodeSupport;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;


/** Emits methods from a resolved host plan; discovery belongs to the caller. */
final class HostEmitter implements Opcodes {
	private final ClassVisitor cv;
	private final AotChunkSizes chunks;
	private final String className;
	private final boolean isInterface;
	private final Set<AotMemberMetadata> readers;
	private final Set<AotMemberMetadata> writers;
	private final Set<AotConstructorMetadata> constructors;
	private final int classIndex;
	private final String ACCESSOR_IMPL_FACTORY_INTERNAL;

	HostEmitter(ClassVisitor cv, AotTypeMetadata host, Set<AotMemberMetadata> readers,
			Set<AotMemberMetadata> writers, Set<AotConstructorMetadata> constructors, int classIndex, String runtimeHooks, AotChunkSizes chunks) {
		this.cv = cv;
		this.chunks = chunks;
		this.className = fqcnToName( host.host() );
		this.isInterface = host.isInterface();
		this.readers = new TreeSet<>( readers );
		this.writers = new TreeSet<>( writers );
		this.constructors = new TreeSet<>( constructors );
		this.classIndex = classIndex;
		this.ACCESSOR_IMPL_FACTORY_INTERNAL = fqcnToName( runtimeHooks );
	}

	void emit() {
		generateReadMethod();
		generateWriteMethod();
		generateCreateMethod();
		generateAccessorMethods();
	}

	private void generateAccessorMethods() {
		// Field and method lookups share the same read/write dispatch tables. Advance the index across
		// both kinds of member, using the same iteration order as generateReadSwitch/generateWriteSwitch.
		List<NameAndIndex> fieldReaderEntries = new ArrayList<>();
		List<NameAndIndex> methodReaderEntries = new ArrayList<>();
		addAccessorEntries( readers, fieldReaderEntries, methodReaderEntries );

		List<NameAndIndex> fieldWriterEntries = new ArrayList<>();
		List<NameAndIndex> methodWriterEntries = new ArrayList<>();
		addAccessorEntries( writers, fieldWriterEntries, methodWriterEntries );

		List<NameAndIndex> instantiatorEntries = new ArrayList<>();
		{
			int index = 0;
			for ( AotConstructorMetadata constructor : constructors ) {
				instantiatorEntries.add( new NameAndIndex( constructor.descriptor(), index++ ) );
			}
		}

		generateAccessorMethod( METHOD_NAME_FIELD_READER_ACCESSOR, fieldReaderEntries,
				classIndex, READER_INTERFACE_INTERNAL, "createReader" );
		generateAccessorMethod( METHOD_NAME_METHOD_READER_ACCESSOR, methodReaderEntries,
				classIndex, READER_INTERFACE_INTERNAL, "createReader" );
		generateAccessorMethod( METHOD_NAME_FIELD_WRITER_ACCESSOR, fieldWriterEntries,
				classIndex, WRITER_INTERFACE_INTERNAL, "createWriter" );
		generateAccessorMethod( METHOD_NAME_METHOD_WRITER_ACCESSOR, methodWriterEntries,
				classIndex, WRITER_INTERFACE_INTERNAL, "createWriter" );
		generateAccessorMethod( METHOD_NAME_INSTANTIATOR_ACCESSOR, instantiatorEntries,
				classIndex, INSTANTIATOR_INTERFACE_INTERNAL, "createInstantiator" );
	}

	private static void addAccessorEntries(Collection<AotMemberMetadata> members,
			List<NameAndIndex> fieldEntries, List<NameAndIndex> methodEntries) {
		int index = 0;
		for ( AotMemberMetadata member : members ) {
			NameAndIndex entry = new NameAndIndex( member.lookupKey(), index++ );
			// AotMemberMetadata is sealed to fields and methods; the final branch is unreachable
			// unless that permit list grows, in which case emission must be taught the new kind.
			if ( member instanceof AotFieldMetadata ) {
				fieldEntries.add( entry );
			}
			else if ( member instanceof AotMethodMetadata ) {
				methodEntries.add( entry );
			}
			else {
				throw new IllegalStateException( "Unexpected member metadata: " + member.getClass() );
			}
		}
	}

	private void generateAccessorMethod(String methodName, List<NameAndIndex> entries,
			int classIndex, String returnTypeInternal, String factoryMethodName) {
		String returnDesc = "L" + returnTypeInternal + ";";
		String descriptor = "(Ljava/lang/String;)" + returnDesc;

		if ( entries.isEmpty() ) {
			generateNullReturnMethod( methodName, descriptor );
			return;
		}

		List<String> names = entries.stream().map( NameAndIndex::name ).toList();

		if ( names.size() <= chunks.memberLookup() ) {
			generateAccessorSwitch( methodName, descriptor, names, entries, classIndex,
					returnTypeInternal, factoryMethodName );
		}
		else {
			int numChunks = ( names.size() - 1 ) / chunks.memberLookup() + 1;

			List<List<String>> chunkNames = new ArrayList<>();
			List<List<NameAndIndex>> chunkEntries = new ArrayList<>();
			for ( int i = 0; i < numChunks; i++ ) {
				chunkNames.add( new ArrayList<>() );
				chunkEntries.add( new ArrayList<>() );
			}

			// Hash partitioning lets a lookup choose one bucket directly. Keep the original member
			// indexes: the position inside a hash bucket is not its position in the dispatch table.
			for ( int i = 0; i < entries.size(); i++ ) {
				int bucket = ( names.get( i ).hashCode() & 0x7FFFFFFF ) % numChunks;
				chunkNames.get( bucket ).add( names.get( i ) );
				chunkEntries.get( bucket ).add( entries.get( i ) );
			}

			for ( int i = 0; i < numChunks; i++ ) {
				if ( !chunkNames.get( i ).isEmpty() ) {
					generateAccessorSwitch( methodName + "$" + i, descriptor,
							chunkNames.get( i ), chunkEntries.get( i ), classIndex,
							returnTypeInternal, factoryMethodName );
				}
			}

			generateAccessorDispatcher( methodName, descriptor, numChunks, chunkNames );
		}
	}

	// NOTE: we are using String switches here instead of putting it all into maps,
	// this is only used at "init" time when we want to retrieve the accessor and NOT at runtime.
	// At runtime, we are using a double-index dispatch (class index, member index).
	//
	// Example generated code (names, types and indexes depend on the registered members):
	// public static ValueReader $$__hibernateAccessor_fieldReader(String key) {
	//     switch (key) {
	//         case "name": return (ValueReader) AccessorImplFactory.createReader(0, 0);
	//         case "age":  return (ValueReader) AccessorImplFactory.createReader(0, 1);
	//         default:     return null;
	//     }
	// }
	// Method keys include the descriptor, e.g. "getAge()I"; constructor keys are e.g. "(I)V".
	// Writers and instantiators use createWriter/createInstantiator and their own member indexes.
	private void generateAccessorSwitch(String methodName, String descriptor,
			List<String> names, List<NameAndIndex> entries, int classIndex,
			String returnTypeInternal, String factoryMethodName) {
		// Hashes are not a size bound: even thousands of distinct keys can have the same hash.
		// Split an oversized bucket by entry count, preserving the original dispatch indexes.
		for ( int start = 0, part = 0; start < names.size(); start += chunks.memberLookup(), part++ ) {
			int end = Math.min( start + chunks.memberLookup(), names.size() );
			String current = part == 0 ? methodName : methodName + "$part" + part;
			String next = end == names.size() ? null : methodName + "$part" + ( part + 1 );
			generateAccessorSwitchPart( current, descriptor, names.subList( start, end ), entries.subList( start, end ),
					classIndex, returnTypeInternal, factoryMethodName, next );
		}
	}

	private void generateAccessorSwitchPart(String methodName, String descriptor,
			List<String> names, List<NameAndIndex> entries, int classIndex,
			String returnTypeInternal, String factoryMethodName, String next) {
		MethodVisitor mv = cv.visitMethod( ACC_PUBLIC | ACC_STATIC, methodName,
				descriptor, null, null );
		mv.visitCode();

		Label defaultLabel = new Label();

		emitStringSwitch( mv, 0, 1, names, defaultLabel, (caseMv, caseIdx) -> {
			int memberIndex = entries.get( caseIdx ).index();
			pushIntConst( caseMv, classIndex );
			pushIntConst( caseMv, memberIndex );
			caseMv.visitMethodInsn( INVOKESTATIC, ACCESSOR_IMPL_FACTORY_INTERNAL,
					factoryMethodName, "(II)Ljava/lang/Object;", false );
			caseMv.visitTypeInsn( CHECKCAST, returnTypeInternal );
			caseMv.visitInsn( ARETURN );
		} );

		mv.visitLabel( defaultLabel );
		mv.visitFrame( F_SAME, 0, null, 0, null );
		if ( next != null ) {
			// A miss in this part must still search the rest of the hash bucket.
			mv.visitVarInsn( ALOAD, 0 );
			mv.visitMethodInsn( INVOKESTATIC, className, next, descriptor, isInterface );
		}
		else {
			// Null means this host has no accessor for the requested key. The factory decides whether
			// to reject the request or delegate to its reflection fallback.
			mv.visitInsn( ACONST_NULL );
		}
		mv.visitInsn( ARETURN );

		mv.visitMaxs( 0, 0 );
		mv.visitEnd();
	}

	// NOTE: we are using String switches here instead of putting it all into maps,
	// this is only used at "init" time when we want to retrieve the accessor and NOT at runtime.
	// At runtime, we are using a double-index dispatch (class index, member index).
	//
	// Generated lookup dispatcher; numChunks is a constant computed at build time:
	// public static ValueReader $$__hibernateAccessor_fieldReader(String key) {
	//     switch ((key.hashCode() & 0x7FFFFFFF) % numChunks) {
	//         case 0: return $$__hibernateAccessor_fieldReader$0(key);
	//         case 1: return $$__hibernateAccessor_fieldReader$1(key);
	//         // Empty buckets and unmatched keys return null.
	//         default: return null;
	//     }
	// }
	private void generateAccessorDispatcher(String methodName, String descriptor,
			int numChunks, List<List<String>> chunks) {
		MethodVisitor mv = cv.visitMethod( ACC_PUBLIC | ACC_STATIC, methodName,
				descriptor, null, null );
		mv.visitCode();

		Label defaultLabel = new Label();
		Label[] labels = new Label[numChunks];
		for ( int i = 0; i < numChunks; i++ ) {
			labels[i] = chunks.get( i ).isEmpty() ? defaultLabel : new Label();
		}

		mv.visitVarInsn( ALOAD, 0 );
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
				mv.visitVarInsn( ALOAD, 0 );
				mv.visitMethodInsn( INVOKESTATIC, className, methodName + "$" + i,
						descriptor, isInterface );
				mv.visitInsn( ARETURN );
			}
		}

		mv.visitLabel( defaultLabel );
		mv.visitFrame( F_SAME, 0, null, 0, null );
		mv.visitInsn( ACONST_NULL );
		mv.visitInsn( ARETURN );

		mv.visitMaxs( 0, 0 );
		mv.visitEnd();
	}

	private void generateReadMethod() {
		String descriptor = "(ILjava/lang/Object;)Ljava/lang/Object;";
		int count = readers.size();

		if ( count == 0 ) {
			generateThrowOnlyMethod( PREFIX_READ_METHOD, descriptor );
			return;
		}

		if ( count <= chunks.memberRead() ) {
			generateReadSwitch( PREFIX_READ_METHOD, descriptor, readers, 0 );
		}
		else {
			ArrayList<AotMemberMetadata> memberList = new ArrayList<>( readers );
			for ( int chunk = 0; chunk * chunks.memberRead() < count; chunk++ ) {
				int start = chunk * chunks.memberRead();
				int end = Math.min( start + chunks.memberRead(), count );
				generateReadSwitch( PREFIX_READ_METHOD + "$" + chunk, descriptor,
						memberList.subList( start, end ), start );
			}
			generateChunkDispatcher( PREFIX_READ_METHOD, descriptor, count, false, chunks.memberRead() );
		}
	}

	// Example generated code (names, types and indexes depend on the registered members):
	// public static Object $$__hibernateAccessor_read(int memberIndex, Object target) {
	//     switch (memberIndex) {
	//         case 0: return ((Model) target).name;
	//         case 1: return Integer.valueOf(((Model) target).getAge());
	//         default: throw new IllegalArgumentException("Unknown member index " + memberIndex + " for Model");
	//     }
	// }
	// A chunk helper has the same signature, but its case indexes start at indexOffset.
	private void generateReadSwitch(String methodName, String descriptor,
			Collection<AotMemberMetadata> members, int indexOffset) {
		int accessFlags = ACC_PUBLIC | ACC_STATIC;
		MethodVisitor mv = cv.visitMethod( accessFlags, methodName, descriptor, null, null );
		mv.visitCode();

		int count = members.size();
		Label[] labels = new Label[count];
		for ( int i = 0; i < count; i++ ) {
			labels[i] = new Label();
		}
		Label defaultLabel = new Label();

		mv.visitVarInsn( ILOAD, 0 );
		mv.visitTableSwitchInsn( indexOffset, indexOffset + count - 1, defaultLabel, labels );

		int index = 0;
		for ( AotMemberMetadata member : members ) {
			mv.visitLabel( labels[index++] );
			mv.visitFrame( F_SAME, 0, null, 0, null );

			// Use the declaring class, not the host or runtime receiver type, so inherited and
			// shadowed fields resolve to the member that was registered.
			String targetClass = fqcnToName( member.declaringClass() );

			mv.visitVarInsn( ALOAD, 1 );
			mv.visitTypeInsn( CHECKCAST, targetClass );

			if ( member instanceof AotFieldMetadata fm ) {
				mv.visitFieldInsn( GETFIELD, targetClass, fm.name(), fm.descriptor() );
				if ( fm.isPrimitive() ) {
					boxPrimitive( mv, fm.descriptor() );
				}
			}
			else if ( member instanceof AotMethodMetadata mm ) {
				int opcode = mm.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL;
				mv.visitMethodInsn( opcode, targetClass, mm.name(), mm.descriptor(), mm.isInterface() );
				if ( mm.isPrimitive() ) {
					boxPrimitive( mv, mm.returnDescriptor() );
				}
			}
			else {
				// AotMemberMetadata is sealed to fields and methods; reaching this leaves the stack
				// without the value ARETURN below expects, so fail here rather than emit broken code.
				throw new IllegalStateException( "Unexpected member metadata: " + member.getClass() );
			}

			mv.visitInsn( ARETURN );
		}

		mv.visitLabel( defaultLabel );
		mv.visitFrame( F_SAME, 0, null, 0, null );
		throwIllegalArgumentWithIndex( mv, className );

		mv.visitMaxs( 0, 0 );
		mv.visitEnd();
	}

	private void generateWriteMethod() {
		String descriptor = "(ILjava/lang/Object;Ljava/lang/Object;)V";
		int count = writers.size();

		if ( count == 0 ) {
			generateThrowOnlyMethod( PREFIX_WRITE_METHOD, descriptor );
			return;
		}

		if ( count <= chunks.memberWrite() ) {
			generateWriteSwitch( PREFIX_WRITE_METHOD, descriptor, writers, 0 );
		}
		else {
			ArrayList<AotMemberMetadata> memberList = new ArrayList<>( writers );
			for ( int chunk = 0; chunk * chunks.memberWrite() < count; chunk++ ) {
				int start = chunk * chunks.memberWrite();
				int end = Math.min( start + chunks.memberWrite(), count );
				generateWriteSwitch( PREFIX_WRITE_METHOD + "$" + chunk, descriptor,
						memberList.subList( start, end ), start );
			}
			generateChunkDispatcher( PREFIX_WRITE_METHOD, descriptor, count, true, chunks.memberWrite() );
		}
	}

	// Example generated code (names, types and indexes depend on the registered members):
	// public static void $$__hibernateAccessor_write(int memberIndex, Object target, Object value) {
	//     switch (memberIndex) {
	//         case 0: ((Model) target).name = (String) value; return;
	//         case 1: ((Model) target).setAge(((Integer) value).intValue()); return;
	//         default: throw new IllegalArgumentException("Unknown member index " + memberIndex + " for Model");
	//     }
	// }
	// A non-void setter's return value is discarded; chunk cases start at indexOffset.
	private void generateWriteSwitch(String methodName, String descriptor,
			Collection<AotMemberMetadata> members, int indexOffset) {
		int accessFlags = ACC_PUBLIC | ACC_STATIC;
		MethodVisitor mv = cv.visitMethod( accessFlags, methodName, descriptor, null, null );
		mv.visitCode();

		int count = members.size();
		Label[] labels = new Label[count];
		for ( int i = 0; i < count; i++ ) {
			labels[i] = new Label();
		}
		Label defaultLabel = new Label();

		mv.visitVarInsn( ILOAD, 0 );
		mv.visitTableSwitchInsn( indexOffset, indexOffset + count - 1, defaultLabel, labels );

		int index = 0;
		for ( AotMemberMetadata member : members ) {
			mv.visitLabel( labels[index++] );
			mv.visitFrame( F_SAME, 0, null, 0, null );

			String targetClass = fqcnToName( member.declaringClass() );

			mv.visitVarInsn( ALOAD, 1 );
			mv.visitTypeInsn( CHECKCAST, targetClass );

			mv.visitVarInsn( ALOAD, 2 );

			if ( member instanceof AotFieldMetadata fm ) {
				if ( fm.isPrimitive() ) {
					BytecodeSupport.emitWideningUnbox( mv, Type.getType( fm.descriptor() ) );
				}
				else {
					mv.visitTypeInsn( CHECKCAST, Type.getType( fm.descriptor() ).getInternalName() );
				}
				mv.visitFieldInsn( PUTFIELD, targetClass, fm.name(), fm.descriptor() );
			}
			else if ( member instanceof AotMethodMetadata mm ) {
				Type paramType = Type.getArgumentTypes( mm.descriptor() )[0];
				if ( mm.isPrimitive() ) {
					BytecodeSupport.emitWideningUnbox( mv, paramType );
				}
				else {
					mv.visitTypeInsn( CHECKCAST, paramType.getInternalName() );
				}
				int opcode = mm.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL;
				mv.visitMethodInsn( opcode, targetClass, mm.name(), mm.descriptor(), mm.isInterface() );
				// One-argument methods may be fluent setters. ValueWriter returns void, so discard
				// their result, accounting for the two stack slots used by long and double.
				if ( !"V".equals( mm.returnDescriptor() ) ) {
					Type returnType = Type.getType( mm.returnDescriptor() );
					if ( returnType.getSize() == 2 ) {
						mv.visitInsn( POP2 );
					}
					else {
						mv.visitInsn( POP );
					}
				}
			}
			else {
				// AotMemberMetadata is sealed to fields and methods; reaching this would leave the
				// receiver and value pushed above on the stack, so fail here instead of emitting them.
				throw new IllegalStateException( "Unexpected member metadata: " + member.getClass() );
			}

			mv.visitInsn( RETURN );
		}

		mv.visitLabel( defaultLabel );
		mv.visitFrame( F_SAME, 0, null, 0, null );
		throwIllegalArgumentWithIndex( mv, className );

		mv.visitMaxs( 0, 0 );
		mv.visitEnd();
	}

	private void generateCreateMethod() {
		String descriptor = "(I[Ljava/lang/Object;)Ljava/lang/Object;";
		int count = constructors.size();

		if ( count == 0 ) {
			generateThrowOnlyMethod( PREFIX_CREATE_METHOD, descriptor );
			return;
		}

		// A constructor case may unbox many arguments. Bound conversions per helper, not just cases.
		int maxParameters = constructors.stream().mapToInt( ctor -> ctor.parameters().size() ).max().orElse( 1 );
		int chunkSize = Math.max( 1, chunks.memberConstructor() / Math.max( 1, maxParameters ) );
		if ( count <= chunkSize ) {
			generateCreateSwitch( PREFIX_CREATE_METHOD, descriptor, constructors, 0 );
		}
		else {
			ArrayList<AotConstructorMetadata> memberList = new ArrayList<>( constructors );
			for ( int chunk = 0; chunk * chunkSize < count; chunk++ ) {
				int start = chunk * chunkSize;
				int end = Math.min( start + chunkSize, count );
				generateCreateSwitch( PREFIX_CREATE_METHOD + "$" + chunk, descriptor,
						memberList.subList( start, end ), start );
			}
			generateChunkDispatcher( PREFIX_CREATE_METHOD, descriptor, count, false, chunkSize );
		}
	}

	// Example generated code (names, types and indexes depend on the registered members):
	// public static Object $$__hibernateAccessor_create(int memberIndex, Object[] arguments) {
	//     switch (memberIndex) {
	//         case 0: return new Model();
	//         case 1: return new Model((String) arguments[0], ((Integer) arguments[1]).intValue());
	//         default: throw new IllegalArgumentException("Unknown member index " + memberIndex + " for Model");
	//     }
	// }
	// A chunk helper preserves the original constructor indexes by starting at indexOffset.
	private void generateCreateSwitch(String methodName, String descriptor,
			Collection<AotConstructorMetadata> ctors, int indexOffset) {
		int accessFlags = ACC_PUBLIC | ACC_STATIC;
		MethodVisitor mv = cv.visitMethod( accessFlags, methodName, descriptor, null, null );
		mv.visitCode();

		int count = ctors.size();
		Label[] labels = new Label[count];
		for ( int i = 0; i < count; i++ ) {
			labels[i] = new Label();
		}
		Label defaultLabel = new Label();

		mv.visitVarInsn( ILOAD, 0 );
		mv.visitTableSwitchInsn( indexOffset, indexOffset + count - 1, defaultLabel, labels );

		int index = 0;
		for ( AotConstructorMetadata ctor : ctors ) {
			mv.visitLabel( labels[index++] );
			mv.visitFrame( F_SAME, 0, null, 0, null );

			Label validArity = new Label();
			mv.visitVarInsn( ALOAD, 1 );
			mv.visitInsn( ARRAYLENGTH );
			pushIntConst( mv, ctor.parameters().size() );
			mv.visitJumpInsn( IF_ICMPEQ, validArity );
			mv.visitTypeInsn( NEW, "java/lang/IllegalArgumentException" );
			mv.visitInsn( DUP );
			mv.visitLdcInsn( "Wrong number of constructor arguments" );
			mv.visitMethodInsn( INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V", false );
			mv.visitInsn( ATHROW );
			mv.visitLabel( validArity );
			String targetClass = fqcnToName( ctor.declaringClass() );

			mv.visitTypeInsn( NEW, targetClass );
			mv.visitInsn( DUP );

			for ( int p = 0; p < ctor.parameters().size(); p++ ) {
				AotParameterMetadata param = ctor.parameters().get( p );
				mv.visitVarInsn( ALOAD, 1 );
				pushIntConst( mv, p );
				mv.visitInsn( AALOAD );

				Type paramType = Type.getType( param.descriptor() );
				if ( param.isPrimitive() ) {
					BytecodeSupport.emitWideningUnbox( mv, paramType );
				}
				else {
					mv.visitTypeInsn( CHECKCAST, paramType.getInternalName() );
				}
			}

			mv.visitMethodInsn( INVOKESPECIAL, targetClass, "<init>", ctor.descriptor(), false );
			mv.visitInsn( ARETURN );
		}

		mv.visitLabel( defaultLabel );
		mv.visitFrame( F_SAME, 0, null, 0, null );
		throwIllegalArgumentWithIndex( mv, className );

		mv.visitMaxs( 0, 0 );
		mv.visitEnd();
	}

	// Generated member dispatcher; the selected chunk size is embedded as an integer constant:
	// public static Object $$__hibernateAccessor_read(int memberIndex, Object target) {
	//     switch (memberIndex / chunkSize) {
	//         case 0: return $$__hibernateAccessor_read$0(memberIndex, target);
	//         case 1: return $$__hibernateAccessor_read$1(memberIndex, target);
	//         default: throw new IllegalArgumentException("Unknown member index " + memberIndex + " for Model");
	//     }
	// }
	// Write/create dispatchers forward their respective arguments; writes return void.
	// Helpers check the original index, including negative or out-of-range indexes.
	private void generateChunkDispatcher(String methodName, String descriptor,
			int totalCount, boolean returnsVoid, int chunkSize) {
		int accessFlags = ACC_PUBLIC | ACC_STATIC;
		MethodVisitor mv = cv.visitMethod( accessFlags, methodName, descriptor, null, null );
		mv.visitCode();

		int chunkCount = ( totalCount - 1 ) / chunkSize + 1;
		Label[] labels = new Label[chunkCount];
		for ( int i = 0; i < chunkCount; i++ ) {
			labels[i] = new Label();
		}
		Label defaultLabel = new Label();

		mv.visitVarInsn( ILOAD, 0 );
		pushIntConst( mv, chunkSize );
		mv.visitInsn( IDIV );
		mv.visitTableSwitchInsn( 0, chunkCount - 1, defaultLabel, labels );

		Type[] argTypes = Type.getArgumentTypes( descriptor );

		for ( int i = 0; i < chunkCount; i++ ) {
			mv.visitLabel( labels[i] );
			mv.visitFrame( F_SAME, 0, null, 0, null );

			for ( int a = 0, slot = 0; a < argTypes.length; a++ ) {
				mv.visitVarInsn( argTypes[a].getOpcode( ILOAD ), slot );
				slot += argTypes[a].getSize();
			}

			mv.visitMethodInsn( INVOKESTATIC, className,
					methodName + "$" + i, descriptor, isInterface );

			if ( returnsVoid ) {
				mv.visitInsn( RETURN );
			}
			else {
				mv.visitInsn( ARETURN );
			}
		}

		mv.visitLabel( defaultLabel );
		mv.visitFrame( F_SAME, 0, null, 0, null );
		throwIllegalArgumentWithIndex( mv, className );

		mv.visitMaxs( 0, 0 );
		mv.visitEnd();
	}

	// Example generated code (names, types and indexes depend on the registered members):
	// public static Object $$__hibernateAccessor_read(int memberIndex, Object target) {
	//     throw new IllegalArgumentException("Unknown member index " + memberIndex + " for Model");
	// }
	// The corresponding write/create signature is used when that member set is empty.
	private void generateThrowOnlyMethod(String methodName, String descriptor) {
		MethodVisitor mv = cv.visitMethod( ACC_PUBLIC | ACC_STATIC, methodName, descriptor, null, null );
		mv.visitCode();
		throwIllegalArgumentWithIndex( mv, className );
		mv.visitMaxs( 0, 0 );
		mv.visitEnd();
	}

	// Example generated lookup for an empty member set (also used for writers and instantiators):
	// public static ValueReader $$__hibernateAccessor_fieldReader(String key) {
	//     return null;
	// }
	private void generateNullReturnMethod(String methodName, String descriptor) {
		MethodVisitor mv = cv.visitMethod( ACC_PUBLIC | ACC_STATIC, methodName, descriptor, null, null );
		mv.visitCode();
		mv.visitInsn( ACONST_NULL );
		mv.visitInsn( ARETURN );
		mv.visitMaxs( 0, 0 );
		mv.visitEnd();
	}

	private static void throwIllegalArgumentWithIndex(MethodVisitor mv, String hostClass) {
		mv.visitTypeInsn( NEW, "java/lang/IllegalArgumentException" );
		mv.visitInsn( DUP );
		mv.visitTypeInsn( NEW, "java/lang/StringBuilder" );
		mv.visitInsn( DUP );
		mv.visitLdcInsn( "Unknown member index " );
		mv.visitMethodInsn( INVOKESPECIAL, "java/lang/StringBuilder", "<init>",
				"(Ljava/lang/String;)V", false );
		mv.visitVarInsn( ILOAD, 0 );
		mv.visitMethodInsn( INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
				"(I)Ljava/lang/StringBuilder;", false );
		mv.visitLdcInsn( " for " + hostClass.replace( '/', '.' ) );
		mv.visitMethodInsn( INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
				"(Ljava/lang/String;)Ljava/lang/StringBuilder;", false );
		mv.visitMethodInsn( INVOKEVIRTUAL, "java/lang/StringBuilder", "toString",
				"()Ljava/lang/String;", false );
		mv.visitMethodInsn( INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>",
				"(Ljava/lang/String;)V", false );
		mv.visitInsn( ATHROW );
	}

	// Generated expression for an int value; other primitives use their corresponding wrapper:
	// Integer.valueOf(value)
	private static void boxPrimitive(MethodVisitor mv, String descriptor) {
		Type primitiveType = Type.getType( descriptor );
		Type wrapperType = BytecodeSupport.boxedType( primitiveType );
		mv.visitMethodInsn( INVOKESTATIC, wrapperType.getInternalName(), "valueOf",
				Type.getMethodDescriptor( wrapperType, primitiveType ), false );
	}
}

record NameAndIndex(String name, int index) {
}
