package org.hibernate.accessor.asm.aot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Type;

/**
 * Build-time emission for entity-injected accessors with host/member double dispatch.
 * This generator neither discovers members nor defines classes. Callers select readers and writers;
 * the host visitor makes requested fields writable and emits host methods after enhancement.
 * Generated code needs core accessor APIs and the supplied runtime hooks, but no ASM or generator classes.
 * Public dispatch methods expose registered members; callers own classloader and module access.
 */
public final class AsmAotGenerator {
	private final GenerationNames names;
	private final AotChunkSizes chunks;

	public AsmAotGenerator(GenerationNames names) {
		this( names, AotChunkSizes.DEFAULT );
	}

	/** Uses the supplied sizes for host emission, shared implementations and factory lookup. */
	public AsmAotGenerator(GenerationNames names, AotChunkSizes chunks) {
		this.names = Objects.requireNonNull( names );
		this.chunks = Objects.requireNonNull( chunks );
	}

	/**
	 * Visits an existing host, removes final from its requested field writers, and emits dispatch and lookup
	 * methods at visitEnd. Fields must be declared by the host; record fields cannot have writers.
	 * Reader methods must take no parameters and return a value; writer methods must take exactly one
	 * parameter; every method's returnDescriptor must agree with its descriptor.
	 * The downstream writer must compute frames/maxima for generated methods. The host index must match
	 * its position in the list passed to {@link #generateClasses}. Input metadata is copied; enhanced method
	 * readers can be supplied through {@link AotHostClassVisitor#addMethodReader} before visitEnd.
	 */
	public AotHostClassVisitor hostVisitor(ClassVisitor visitor, AotTypeMetadata host, Set<AotMemberMetadata> readers,
			Set<AotMemberMetadata> writers, Set<AotConstructorMetadata> constructors, int hostIndex) {
		validateReaders( host, readers );
		validateWriters( host, writers );
		return new AotHostClassVisitor( this, visitor, host, readers, writers, constructors, hostIndex );
	}

	/** Emits methods before the downstream visitor's visitEnd. The writer must compute frames/maxima.
	 * The host index must match its position in the list passed to generateClasses.
	 * Members are sorted once for matching dispatch and lookup indexes; caller collections are not mutated.
	 * This low-level emitter does not transform field declarations; use {@link #hostVisitor} when transforming
	 * an existing class. Fields must be declared by the host and writable fields must already be non-final.
	 * Reader and writer methods are validated for arity and descriptor consistency as in {@link #hostVisitor}.
	 */
	public void emitHostMethods(ClassVisitor visitor, AotTypeMetadata host, Set<AotMemberMetadata> readers,
			Set<AotMemberMetadata> writers, Set<AotConstructorMetadata> constructors, int hostIndex) {
		validateReaders( host, readers );
		validateWriters( host, writers );
		new HostEmitter( visitor, host, readers, writers, constructors, hostIndex, names.runtimeHooks(), chunks ).emit();
	}

	/**
	 * Rejects reader registrations the read dispatch cannot emit. generateReadSwitch pushes the
	 * receiver, invokes the member and returns its value unconditionally, so a void getter or a
	 * getter taking parameters would only fail as a VerifyError when the application's own entity
	 * class is loaded, far from the build step that registered it. Checking here keeps the failure
	 * local to the caller that supplied the metadata.
	 */
	private static void validateReaders(AotTypeMetadata host, Set<AotMemberMetadata> readers) {
		for ( AotMemberMetadata member : readers ) {
			if ( member instanceof AotFieldMetadata fm ) {
				validateFieldHost( host, fm );
			}
			else if ( member instanceof AotMethodMetadata mm ) {
				validateMethodDescriptor( host, mm );
				if ( "V".equals( mm.returnDescriptor() ) ) {
					throw new IllegalArgumentException( "Reader method " + member( mm ) + " registered for accessor host "
							+ host.host() + " must return a value, but returns void" );
				}
				int parameters = Type.getArgumentTypes( mm.descriptor() ).length;
				if ( parameters != 0 ) {
					throw new IllegalArgumentException( "Reader method " + member( mm ) + " registered for accessor host "
							+ host.host() + " must take no parameters, but takes " + parameters );
				}
			}
			else {
				throw new IllegalStateException( "Unexpected member metadata: " + member.getClass() );
			}
		}
	}

	/**
	 * Rejects writer registrations the write dispatch cannot emit. generateWriteSwitch reads the
	 * single parameter type without checking arity, so a zero-argument setter fails inside the
	 * generator with an ArrayIndexOutOfBoundsException naming no member, and a setter taking two or
	 * more arguments is a VerifyError at entity-class load time. Both become build errors here.
	 */
	private static void validateWriters(AotTypeMetadata host, Set<AotMemberMetadata> writers) {
		for ( AotMemberMetadata member : writers ) {
			if ( member instanceof AotFieldMetadata fm ) {
				validateFieldHost( host, fm );
			}
			else if ( member instanceof AotMethodMetadata mm ) {
				validateMethodDescriptor( host, mm );
				int parameters = Type.getArgumentTypes( mm.descriptor() ).length;
				if ( parameters != 1 ) {
					throw new IllegalArgumentException( "Writer method " + member( mm ) + " registered for accessor host "
							+ host.host() + " must take exactly one parameter, but takes " + parameters );
				}
			}
			else {
				throw new IllegalStateException( "Unexpected member metadata: " + member.getClass() );
			}
		}
	}

	/** Fields are accessed through the host itself, so an inherited or foreign field cannot be registered here. */
	private static void validateFieldHost(AotTypeMetadata host, AotFieldMetadata field) {
		if ( !host.host().equals( field.declaringClass() ) ) {
			throw new IllegalArgumentException( "Field " + field.declaringClass() + "#" + field.name()
					+ " must use its declaring class as accessor host, not " + host.host() );
		}
	}

	/** returnDescriptor denormalizes the descriptor's return type; emission trusts it for boxing and popping. */
	private static void validateMethodDescriptor(AotTypeMetadata host, AotMethodMetadata method) {
		String expected = Type.getReturnType( method.descriptor() ).getDescriptor();
		if ( !expected.equals( method.returnDescriptor() ) ) {
			throw new IllegalArgumentException( "Method " + member( method ) + " registered for accessor host "
					+ host.host() + " declares return descriptor " + method.returnDescriptor()
					+ ", but its descriptor returns " + expected );
		}
	}

	private static String member(AotMethodMetadata method) {
		return method.declaringClass() + "#" + method.name() + " " + method.descriptor();
	}

	/** Generates bridges, the three shared implementations, and the factory, keyed by binary name.
	 * The factory exposes create(), or create(AccessorFactory) when fallback is enabled.
	 * Both are first-call-wins: the singleton is built by whichever call finds it uninitialized, and
	 * later calls return it unchanged, discarding any fallback they were given. Install the intended
	 * fallback on the first call; there is no way to replace it afterwards.
	 * Both multi-value factory methods throw MultiValueAccessorGenerationException.
	 */
	public Map<String, byte[]> generateClasses(List<AotTypeMetadata> hosts, boolean withFallback) {
		hosts = List.copyOf( hosts );
		Map<String, byte[]> output = new LinkedHashMap<>();
		for ( AotTypeMetadata host : hosts ) {
			if ( host.needsBridge() ) {
				output.put( host.dispatchTarget(), generateBridge( host ) );
			}
		}
		output.put( names.reader(), generateReader( hosts ) );
		output.put( names.writer(), generateWriter( hosts ) );
		output.put( names.instantiator(), generateInstantiator( hosts ) );
		output.put( names.factory(), generateFactory( hosts, withFallback ) );
		return output;
	}

	/** Emits a public bridge. Define it alongside the host, using host.dispatchTarget() as its name. */
	public byte[] generateBridge(AotTypeMetadata host) {
		if ( !host.needsBridge() ) {
			throw new IllegalArgumentException( "A public host does not need a bridge: " + host.host() );
		}
		return new BridgeGenerator().generate( host.host(), host.isInterface() );
	}

	/** Emits the shared reader implementation using the configured binary name. */
	public byte[] generateReader(List<AotTypeMetadata> hosts) {
		return new SingleImplGenerator( names, chunks ).generateReaderImpl( List.copyOf( hosts ) );
	}

	/** Emits the shared writer implementation using the configured binary name. */
	public byte[] generateWriter(List<AotTypeMetadata> hosts) {
		return new SingleImplGenerator( names, chunks ).generateWriterImpl( List.copyOf( hosts ) );
	}

	/** Emits the shared instantiator implementation using the configured binary name. */
	public byte[] generateInstantiator(List<AotTypeMetadata> hosts) {
		return new SingleImplGenerator( names, chunks ).generateInstantiatorImpl( List.copyOf( hosts ) );
	}

	/** Emits the factory alone; callers must also install its host methods, bridges and implementations.
	 * The generated create(), or create(AccessorFactory) when fallback is enabled, is first-call-wins:
	 * only the first call constructs the singleton and honours its fallback argument; later calls
	 * return the existing instance and discard theirs.
	 */
	public byte[] generateFactory(List<AotTypeMetadata> hosts, boolean withFallback) {
		FactoryGenerator factory = new FactoryGenerator( names, chunks.typeLookup() );
		for ( AotTypeMetadata host : List.copyOf( hosts ) ) {
			factory.registerDispatchTarget( host.host(), host.dispatchTarget().replace( '.', '/' ), host.dispatchTargetIsInterface() );
			factory.registerFieldReader( host.host() );
			factory.registerMethodReader( host.host() );
			factory.registerFieldWriter( host.host() );
			factory.registerMethodWriter( host.host() );
			factory.registerInstantiator( host.host() );
		}
		return factory.generate( withFallback );
	}

}
