package org.hibernate.accessor.asm.aot;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Transforms a declaring class for single-member access: makes requested fields writable and injects
 * dispatch and lookup methods. Obtain a fresh visitor from {@link AsmAotGenerator#hostVisitor} for each class pass.
 * Every registered field member must match a field declared by the visited class; a member that
 * matches nothing is rejected at {@link #visitEnd()}, before any dispatch is emitted for it.
 */
public final class AotHostClassVisitor extends ClassVisitor {
	private final AsmAotGenerator generator;
	private final AotTypeMetadata host;
	private final Set<AotMemberMetadata> readers;
	private final Set<AotMemberMetadata> writers;
	private final Set<AotConstructorMetadata> constructors;
	private final Set<AotFieldMetadata> fieldWriters;
	// Field members still waiting for a matching declaration; emptied as visitField reports them.
	private final Set<AotFieldMetadata> unmatchedFields;
	private final int hostIndex;
	private boolean ended;

	AotHostClassVisitor(AsmAotGenerator generator, ClassVisitor visitor, AotTypeMetadata host,
			Set<AotMemberMetadata> readers, Set<AotMemberMetadata> writers,
			Set<AotConstructorMetadata> constructors, int hostIndex) {
		super( Opcodes.ASM9, visitor );
		this.generator = generator;
		this.host = host;
		this.readers = new HashSet<>( readers );
		this.writers = Set.copyOf( writers );
		this.constructors = Set.copyOf( constructors );
		this.fieldWriters = fields( writers ).collect( Collectors.toUnmodifiableSet() );
		// Every field member's declaring class is the host, so the host must declare all of them.
		// Sorted for a stable message when several are missing.
		this.unmatchedFields = Stream.concat( fields( readers ), fields( writers ) )
				.collect( Collectors.toCollection( TreeSet::new ) );
		this.hostIndex = hostIndex;
	}

	/**
	 * Adds a method reader discovered during upstream enhancement. Call before {@link #visitEnd()};
	 * all readers are assigned their dispatch and lookup indexes together at the end of the class.
	 * Caller-owned metadata collections are never modified.
	 */
	public void addMethodReader(AotMethodMetadata reader) {
		if ( ended ) {
			throw new IllegalStateException( "Host methods have already been emitted for " + host.host() );
		}
		readers.add( Objects.requireNonNull( reader ) );
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		if ( !host.host().equals( name.replace( '/', '.' ) ) ) {
			throw new IllegalArgumentException( "Expected accessor host " + host.host() + " but visited " + name );
		}
		if ( ( access & Opcodes.ACC_RECORD ) != 0 && !fieldWriters.isEmpty() ) {
			throw new IllegalArgumentException( "Cannot register field writers for record " + host.host() );
		}
		super.visit( version, access, name, signature, superName, interfaces );
	}

	private static Stream<AotFieldMetadata> fields(Set<AotMemberMetadata> members) {
		return members.stream().filter( AotFieldMetadata.class::isInstance ).map( AotFieldMetadata.class::cast );
	}

	@Override
	public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
		unmatchedFields.removeIf( field -> field.name().equals( name ) && field.descriptor().equals( descriptor ) );
		if ( fieldWriters.stream().anyMatch( field -> field.name().equals( name ) && field.descriptor().equals( descriptor ) ) ) {
			if ( ( access & Opcodes.ACC_STATIC ) != 0 ) {
				throw new IllegalArgumentException( "Cannot register a writer for static field " + host.host() + "#" + name );
			}
			// Generated writers use PUTFIELD outside a constructor, which requires a non-final field.
			access &= ~Opcodes.ACC_FINAL;
		}
		return super.visitField( access, name, descriptor, signature, value );
	}

	@Override
	public void visitEnd() {
		// A field member matching no declaration would otherwise be emitted as a GETFIELD/PUTFIELD
		// against a field that does not exist, failing as a NoSuchFieldError on first access.
		if ( !unmatchedFields.isEmpty() ) {
			String missing = unmatchedFields.stream().map( field -> field.name() + " " + field.descriptor() )
					.collect( Collectors.joining( ", " ) );
			throw new IllegalArgumentException( "Accessor host " + host.host()
					+ " does not declare registered field(s): " + missing );
		}
		ended = true;
		generator.emitHostMethods( cv, host, readers, writers, constructors, hostIndex );
		super.visitEnd();
	}
}
