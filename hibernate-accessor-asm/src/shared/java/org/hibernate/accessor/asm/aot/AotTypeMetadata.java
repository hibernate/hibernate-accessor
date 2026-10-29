package org.hibernate.accessor.asm.aot;

import java.util.Comparator;

/**
 * Identifies a registered type and the host of its injected accessor methods.
 *
 * @param packageName package of the registered type; {@code null} is normalized to the empty string
 * @param name binary name of the registered type
 * @param host binary name of the type to transform
 * @param isPublic whether the host is public
 * @param isInterface whether the host is an interface
 */
public record AotTypeMetadata(  String packageName, String name, String host,
								boolean isPublic, boolean isInterface)
		implements Comparable<AotTypeMetadata> {

	public AotTypeMetadata(String packageName, String name, String host, boolean isPublic, boolean isInterface) {
		this.packageName = packageName == null ? "" : packageName;
		this.name = name;
		this.host = host;
		this.isPublic = isPublic;
		this.isInterface = isInterface;
	}

	// A non-public host (class or interface) gets injected PUBLIC STATIC accessor methods, but the
	// JVM still denies access to them from outside the host's package because the *declaring type*
	// itself isn't visible there. The generated accessor classes always live in a fixed runtime
	// package, so they can't call those methods directly. The bridge is a synthetic PUBLIC class
	// generated in the same package as the host, which can see the host's methods and simply
	// forwards to them; dispatch then targets the bridge instead of the host.
	boolean needsBridge() {
		return !isPublic();
	}

	public String dispatchTarget() {
		if ( needsBridge() ) {
			return BridgeGenerator.bridgeFqcn( host );
		}
		return host;
	}

	// The bridge (when present) is always a plain class, even if the host itself is an interface.
	boolean dispatchTargetIsInterface() {
		return isInterface() && !needsBridge();
	}

	private static final Comparator<AotTypeMetadata> COMPARATOR = Comparator.comparing( AotTypeMetadata::packageName )
			.thenComparing( AotTypeMetadata::name );

	@Override
	public int compareTo(AotTypeMetadata o) {
		return COMPARATOR.compare( this, o );
	}
}
