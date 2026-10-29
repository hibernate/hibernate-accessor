package org.hibernate.accessor.asm.aot;

import java.nio.file.Path;

import org.hibernate.accessor.testing.aot.asm.AsmAotProcessor;

/** Build entry point; selection policy belongs to the TCK runner, not the processor. */
public final class PrepareTck {
	public static void main(String[] args) throws Exception {
		new AsmAotProcessor( new GenerationNames( "tckgenerated.Factory", "tckgenerated.Reader",
				"tckgenerated.Writer", "tckgenerated.Instantiator", "org.hibernate.accessor.asm.aot.TckRuntimeHooks" ) )
				.process( Path.of( args[0] ), Path.of( args[1] ),
						name -> name.startsWith( "org.hibernate.accessor.tck.tests." )
								&& !name.endsWith( "Test" ) && !name.endsWith( "package-info" ),
						false );
	}
}
