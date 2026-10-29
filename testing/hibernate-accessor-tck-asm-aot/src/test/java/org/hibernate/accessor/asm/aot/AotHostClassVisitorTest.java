package org.hibernate.accessor.asm.aot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hibernate.accessor.asm.aot.GeneratorConstants.GENERATED_CLASS_VERSION;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.accessor.AccessorFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class AotHostClassVisitorTest {
	private static final String MODEL = "generated.Model";
	private static final AotTypeMetadata HOST = new AotTypeMetadata( "generated", MODEL, MODEL, true, false );
	private static final GenerationNames NAMES = new GenerationNames( "generated.Factory", "generated.Reader", "generated.Writer",
			"generated.Instantiator", RuntimeHooks.class.getName() );
	private static final AotFieldMetadata VALUE = new AotFieldMetadata( "value", "I", true, MODEL );
	private static final AotFieldMetadata READ_ONLY = new AotFieldMetadata( "readOnly", "I", true, MODEL );
	private static final AotMethodMetadata ENHANCED_READER = new AotMethodMetadata( "$$_hibernate_read_value", "()I", true,
			MODEL, false, "I" );

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void transformsFieldsAndEmitsWorkingDispatchTogether(boolean originallyFinal) throws Exception {
		AsmAotGenerator generator = new AsmAotGenerator( NAMES );
		Set<AotMemberMetadata> readers = Set.of( VALUE, READ_ONLY );
		Set<AotMemberMetadata> writers = Set.of( VALUE );
		byte[] input = model( originallyFinal );
		byte[] transformed = transform( generator, input, readers, writers );
		// A new visitor can reuse the same metadata without leaking dynamically discovered readers.
		assertThat( transform( generator, input, readers, writers ) ).isEqualTo( transformed );
		assertThat( readers ).containsExactlyInAnyOrder( VALUE, READ_ONLY );
		assertThat( writers ).containsExactly( VALUE );
		Map<String, byte[]> output = new HashMap<>( generator.generateClasses( List.of( HOST ), false ) );
		output.put( MODEL, transformed );
		ClassLoader loader = new ClassLoader( getClass().getClassLoader() ) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] bytes = output.get( name );
				if ( bytes == null )
					throw new ClassNotFoundException( name );
				return defineClass( name, bytes, 0, bytes.length );
			}
		};
		RuntimeHooks.loader = loader;
		try {
			AccessorFactory factory = (AccessorFactory) loader.loadClass( "generated.Factory" ).getMethod( "create" ).invoke( null );
			Class<?> type = loader.loadClass( MODEL );
			Object target = factory.instantiator( type.getConstructor() ).create();
			var field = type.getDeclaredField( "value" );
			factory.valueWriter( field ).set( target, 42 );
			assertThat( factory.valueReader( field ).get( target ) ).isEqualTo( 42 );
			assertThat( factory.valueReader( type.getDeclaredMethod( "$$_hibernate_read_value" ) ).get( target ) ).isEqualTo( 42 );
			assertThat( Modifier.isFinal( field.getModifiers() ) ).isFalse();
			var readOnly = type.getDeclaredField( "readOnly" );
			assertThat( Modifier.isFinal( readOnly.getModifiers() ) ).isTrue();
			assertThat( factory.valueReader( readOnly ).get( target ) ).isEqualTo( 7 );
			assertThatThrownBy( () -> factory.valueWriter( readOnly ) ).isInstanceOf( UnsupportedOperationException.class );
		}
		finally {
			RuntimeHooks.loader = null;
		}
	}

	@Test
	void preservesApplicationVersionWhileGeneratedHelpersUseConfiguredVersion() {
		AotTypeMetadata packagePrivateHost = new AotTypeMetadata( "generated", MODEL, MODEL, false, false );
		AsmAotGenerator generator = new AsmAotGenerator( NAMES );
		ClassWriter input = new ClassWriter( 0 );
		input.visit( Opcodes.V21, 0, "generated/Model", null, "java/lang/Object", null );
		input.visitEnd();
		ClassWriter transformed = new ClassWriter( ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS );
		new ClassReader( input.toByteArray() ).accept(
				generator.hostVisitor( transformed, packagePrivateHost, Set.of(), Set.of(), Set.of(), 0 ), 0 );
		assertThat( new ClassReader( transformed.toByteArray() ).readUnsignedShort( 6 ) ).isEqualTo( Opcodes.V21 );
		Map<String, byte[]> helpers = generator.generateClasses( List.of( packagePrivateHost ), false );
		assertThat( helpers ).hasSize( 5 ); // bridge, factory, reader, writer, instantiator
		for ( byte[] bytes : helpers.values() ) {
			assertThat( new ClassReader( bytes ).readUnsignedShort( 6 ) ).isEqualTo( GENERATED_CLASS_VERSION );
		}
	}

	@Test
	void rejectsFieldAccessInAnotherHost() {
		AsmAotGenerator generator = new AsmAotGenerator( NAMES );
		AotFieldMetadata inherited = new AotFieldMetadata( "value", "I", true, "generated.Parent" );
		assertThatThrownBy( () -> generator.hostVisitor( new ClassWriter( 0 ), HOST, Set.of( inherited ), Set.of(), Set.of(), 0 ) )
				.isInstanceOf( IllegalArgumentException.class ).hasMessageContaining( "generated.Parent#value" ).hasMessageContaining( MODEL );
		assertThatThrownBy( () -> generator.hostVisitor( new ClassWriter( 0 ), HOST, Set.of(), Set.of( inherited ), Set.of(), 0 ) )
				.isInstanceOf( IllegalArgumentException.class ).hasMessageContaining( "declaring class" );
		assertThatThrownBy( () -> generator.emitHostMethods( new ClassWriter( 0 ), HOST, Set.of( inherited ), Set.of(), Set.of(), 0 ) )
				.isInstanceOf( IllegalArgumentException.class ).hasMessageContaining( "declaring class" );
	}

	@Test
	void rejectsVoidReaderMethods() {
		// generateReadSwitch emits ARETURN unconditionally, so a void getter is a VerifyError when
		// the application's own entity class loads, long after the build step that registered it.
		AotMethodMetadata voidGetter = new AotMethodMetadata( "getValue", "()V", false, MODEL, false, "V" );
		assertThatThrownBy( () -> readerVisitor( voidGetter ) )
				.isInstanceOf( IllegalArgumentException.class )
				.hasMessageContaining( "Reader method " + MODEL + "#getValue ()V" )
				.hasMessageContaining( MODEL ).hasMessageContaining( "returns void" );
		assertThatThrownBy( () -> readerEmission( voidGetter ) )
				.isInstanceOf( IllegalArgumentException.class ).hasMessageContaining( "returns void" );
	}

	@Test
	void rejectsReaderMethodsTakingParameters() {
		// Only ALOAD 1 / CHECKCAST precede the invoke, so a getter with parameters underflows too.
		AotMethodMetadata indexedGetter = new AotMethodMetadata( "getValue", "(I)I", true, MODEL, false, "I" );
		assertThatThrownBy( () -> readerVisitor( indexedGetter ) )
				.isInstanceOf( IllegalArgumentException.class )
				.hasMessageContaining( "Reader method " + MODEL + "#getValue (I)I" )
				.hasMessageContaining( "must take no parameters, but takes 1" );
		assertThatThrownBy( () -> readerEmission( indexedGetter ) )
				.isInstanceOf( IllegalArgumentException.class ).hasMessageContaining( "must take no parameters" );
	}

	@Test
	void rejectsWriterMethodsWithoutExactlyOneParameter() {
		// generateWriteSwitch reads the first argument type without checking arity: no argument is a
		// bare ArrayIndexOutOfBoundsException inside the generator, more than one a runtime VerifyError.
		AotMethodMetadata noArgument = new AotMethodMetadata( "setValue", "()V", false, MODEL, false, "V" );
		assertThatThrownBy( () -> writerVisitor( noArgument ) )
				.isInstanceOf( IllegalArgumentException.class )
				.hasMessageContaining( "Writer method " + MODEL + "#setValue ()V" )
				.hasMessageContaining( "must take exactly one parameter, but takes 0" );
		assertThatThrownBy( () -> writerEmission( noArgument ) )
				.isInstanceOf( IllegalArgumentException.class ).hasMessageContaining( "but takes 0" );

		AotMethodMetadata twoArguments = new AotMethodMetadata( "setValue", "(II)V", true, MODEL, false, "V" );
		assertThatThrownBy( () -> writerVisitor( twoArguments ) )
				.isInstanceOf( IllegalArgumentException.class )
				.hasMessageContaining( "Writer method " + MODEL + "#setValue (II)V" )
				.hasMessageContaining( "must take exactly one parameter, but takes 2" );
		assertThatThrownBy( () -> writerEmission( twoArguments ) )
				.isInstanceOf( IllegalArgumentException.class ).hasMessageContaining( "but takes 2" );
	}

	@Test
	void rejectsReturnDescriptorDisagreeingWithDescriptor() {
		// returnDescriptor denormalizes the descriptor; emission trusts it for boxing and popping.
		AotMethodMetadata reader = new AotMethodMetadata( "getValue", "()I", true, MODEL, false, "J" );
		assertThatThrownBy( () -> readerVisitor( reader ) )
				.isInstanceOf( IllegalArgumentException.class )
				.hasMessageContaining( "Method " + MODEL + "#getValue ()I" ).hasMessageContaining( MODEL )
				.hasMessageContaining( "declares return descriptor J, but its descriptor returns I" );
		assertThatThrownBy( () -> readerEmission( reader ) )
				.isInstanceOf( IllegalArgumentException.class ).hasMessageContaining( "declares return descriptor J" );

		AotMethodMetadata writer = new AotMethodMetadata( "setValue", "(I)V", true, MODEL, false, "I" );
		assertThatThrownBy( () -> writerVisitor( writer ) )
				.isInstanceOf( IllegalArgumentException.class )
				.hasMessageContaining( "Method " + MODEL + "#setValue (I)V" )
				.hasMessageContaining( "declares return descriptor I, but its descriptor returns V" );
		assertThatThrownBy( () -> writerEmission( writer ) )
				.isInstanceOf( IllegalArgumentException.class ).hasMessageContaining( "but its descriptor returns V" );
	}

	@Test
	void rejectsFieldMembersMatchingNoDeclaredField() {
		// An unmatched field member used to emit a GETFIELD/PUTFIELD for a field that does not
		// exist, which only shows up as a NoSuchFieldError on first access.
		AotFieldMetadata missing = new AotFieldMetadata( "missing", "I", true, MODEL );
		assertThatThrownBy( () -> visitDeclaringOnlyValue( Set.of( VALUE, missing ), Set.of() ) )
				.isInstanceOf( IllegalArgumentException.class )
				.hasMessageContaining( "Accessor host " + MODEL ).hasMessageContaining( "missing I" );
		assertThatThrownBy( () -> visitDeclaringOnlyValue( Set.of(), Set.of( missing ) ) )
				.isInstanceOf( IllegalArgumentException.class )
				.hasMessageContaining( "Accessor host " + MODEL ).hasMessageContaining( "missing I" );
		// A declared field under a different descriptor is still a miss.
		assertThatThrownBy( () -> visitDeclaringOnlyValue( Set.of( new AotFieldMetadata( "value", "J", true, MODEL ) ), Set.of() ) )
				.isInstanceOf( IllegalArgumentException.class ).hasMessageContaining( "value J" );
	}

	private static void visitDeclaringOnlyValue(Set<AotMemberMetadata> readers, Set<AotMemberMetadata> writers) {
		ClassWriter output = new ClassWriter( ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS );
		var visitor = new AsmAotGenerator( NAMES ).hostVisitor( output, HOST, readers, writers, Set.of(), 0 );
		visitor.visit( GENERATED_CLASS_VERSION, Opcodes.ACC_PUBLIC, "generated/Model", null, "java/lang/Object", null );
		visitor.visitField( Opcodes.ACC_PRIVATE, "value", "I", null, null ).visitEnd();
		visitor.visitEnd();
	}

	private static void readerVisitor(AotMemberMetadata reader) {
		new AsmAotGenerator( NAMES ).hostVisitor( new ClassWriter( 0 ), HOST, Set.of( reader ), Set.of(), Set.of(), 0 );
	}

	private static void readerEmission(AotMemberMetadata reader) {
		new AsmAotGenerator( NAMES ).emitHostMethods( new ClassWriter( 0 ), HOST, Set.of( reader ), Set.of(), Set.of(), 0 );
	}

	private static void writerVisitor(AotMemberMetadata writer) {
		new AsmAotGenerator( NAMES ).hostVisitor( new ClassWriter( 0 ), HOST, Set.of(), Set.of( writer ), Set.of(), 0 );
	}

	private static void writerEmission(AotMemberMetadata writer) {
		new AsmAotGenerator( NAMES ).emitHostMethods( new ClassWriter( 0 ), HOST, Set.of(), Set.of( writer ), Set.of(), 0 );
	}

	@Test
	void rejectsRecordFieldWrites() {
		var visitor = new AsmAotGenerator( NAMES ).hostVisitor( new ClassWriter( 0 ), HOST, Set.of(), Set.of( VALUE ), Set.of(), 0 );
		assertThatThrownBy( () -> visitor.visit( GENERATED_CLASS_VERSION, Opcodes.ACC_PUBLIC | Opcodes.ACC_RECORD,
				"generated/Model", null, "java/lang/Record", null ) )
				.isInstanceOf( IllegalArgumentException.class ).hasMessageContaining( "record " + MODEL );
	}

	@Test
	void rejectsStaticFieldWrites() {
		var visitor = new AsmAotGenerator( NAMES ).hostVisitor( new ClassWriter( 0 ), HOST, Set.of(), Set.of( VALUE ), Set.of(), 0 );
		visitor.visit( GENERATED_CLASS_VERSION, Opcodes.ACC_PUBLIC, "generated/Model", null, "java/lang/Object", null );
		assertThatThrownBy( () -> visitor.visitField( Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "value", "I", null, 1 ) )
				.isInstanceOf( IllegalArgumentException.class ).hasMessageContaining( "static field " + MODEL + "#value" );
	}

	@Test
	void matchesFieldDescriptorAndPreservesUnrequestedStaticFields() {
		ClassWriter output = new ClassWriter( ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS );
		var visitor = new AsmAotGenerator( NAMES ).hostVisitor( output, HOST, Set.of(), Set.of( VALUE ), Set.of(), 0 );
		visitor.visit( GENERATED_CLASS_VERSION, Opcodes.ACC_PUBLIC, "generated/Model", null, "java/lang/Object", null );
		visitor.visitField( Opcodes.ACC_FINAL, "value", "I", null, null ).visitEnd();
		visitor.visitField( Opcodes.ACC_FINAL, "value", "J", null, null ).visitEnd();
		visitor.visitField( Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "constant", "I", null, 1 ).visitEnd();
		visitor.visitEnd();
		Map<String, Integer> flags = new HashMap<>();
		new ClassReader( output.toByteArray() ).accept( new ClassVisitor( Opcodes.ASM9 ) {
			@Override
			public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
				flags.put( name + descriptor, access );
				return null;
			}
		}, 0 );
		assertThat( flags.get( "valueI" ) & Opcodes.ACC_FINAL ).isZero();
		assertThat( flags.get( "valueJ" ) & Opcodes.ACC_FINAL ).isNotZero();
		assertThat( flags.get( "constantI" ) & Opcodes.ACC_FINAL ).isNotZero();
	}

	private static byte[] transform(AsmAotGenerator generator, byte[] input, Set<AotMemberMetadata> readers, Set<AotMemberMetadata> writers) {
		ClassReader reader = new ClassReader( input );
		ClassWriter output = new ClassWriter( reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS );
		var visitor = generator.hostVisitor( output, HOST, readers, writers,
				Set.of( new AotConstructorMetadata( MODEL, MODEL, "()V", List.of() ) ), 0 );
		reader.accept( new ClassVisitor( Opcodes.ASM9, visitor ) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				if ( name.equals( ENHANCED_READER.name() ) ) {
					visitor.addMethodReader( ENHANCED_READER );
				}
				return super.visitMethod( access, name, descriptor, signature, exceptions );
			}
		}, 0 );
		return output.toByteArray();
	}

	private static byte[] model(boolean finalField) {
		ClassWriter writer = new ClassWriter( ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS );
		writer.visit( GENERATED_CLASS_VERSION, Opcodes.ACC_PUBLIC, "generated/Model", null, "java/lang/Object", null );
		writer.visitField( Opcodes.ACC_PRIVATE | ( finalField ? Opcodes.ACC_FINAL : 0 ), "value", "I", null, null ).visitEnd();
		writer.visitField( Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "readOnly", "I", null, null ).visitEnd();
		var constructor = writer.visitMethod( Opcodes.ACC_PUBLIC, "<init>", "()V", null, null );
		constructor.visitCode();
		constructor.visitVarInsn( Opcodes.ALOAD, 0 );
		constructor.visitMethodInsn( Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false );
		constructor.visitVarInsn( Opcodes.ALOAD, 0 );
		constructor.visitIntInsn( Opcodes.BIPUSH, 7 );
		constructor.visitFieldInsn( Opcodes.PUTFIELD, "generated/Model", "readOnly", "I" );
		constructor.visitInsn( Opcodes.RETURN );
		constructor.visitMaxs( 0, 0 );
		constructor.visitEnd();
		var method = writer.visitMethod( Opcodes.ACC_PUBLIC, ENHANCED_READER.name(), ENHANCED_READER.descriptor(), null, null );
		method.visitCode();
		method.visitVarInsn( Opcodes.ALOAD, 0 );
		method.visitFieldInsn( Opcodes.GETFIELD, "generated/Model", "value", "I" );
		method.visitInsn( Opcodes.IRETURN );
		method.visitMaxs( 0, 0 );
		method.visitEnd();
		writer.visitEnd();
		return writer.toByteArray();
	}
}
