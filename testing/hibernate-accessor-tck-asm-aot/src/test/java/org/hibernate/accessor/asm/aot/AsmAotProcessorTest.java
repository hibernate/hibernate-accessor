package org.hibernate.accessor.asm.aot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.hibernate.accessor.tck.util.TckHelper;
import org.hibernate.accessor.testing.aot.asm.AsmAotProcessor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AsmAotProcessorTest {
	@TempDir
	Path temporary;

	@Test
	void preservesUnselectedClassesAndResourcesAndProducesRepeatableOutput() throws Exception {
		Path input = temporary.resolve( "input" );
		String resource = AotBean.class.getName().replace( '.', '/' ) + ".class";
		Path bean = input.resolve( resource );
		Files.createDirectories( bean.getParent() );
		try ( var stream = AotBean.class.getClassLoader().getResourceAsStream( resource ) ) {
			Files.copy( stream, bean );
		}
		Files.writeString( input.resolve( "fixture.txt" ), "preserved" );
		var processor = new AsmAotProcessor( new GenerationNames( "example.Factory", "example.Reader",
				"example.Writer", "example.Instantiator", RuntimeHooks.class.getName() ) );
		Path unchanged = temporary.resolve( "unchanged" );
		processor.process( input, unchanged, name -> false, false );
		assertThat( Files.readAllBytes( unchanged.resolve( resource ) ) ).isEqualTo( Files.readAllBytes( bean ) );
		Path first = temporary.resolve( "first" );
		Path second = temporary.resolve( "second" );
		processor.process( input, first, name -> true, false );
		processor.process( input, second, name -> true, false );
		assertThat( Files.readAllBytes( first.resolve( resource ) ) ).isNotEqualTo( Files.readAllBytes( bean ) );
		assertThat( Files.readString( first.resolve( "fixture.txt" ) ) ).isEqualTo( "preserved" );
		try ( var paths = Files.walk( first ) ) {
			for ( Path path : paths.filter( Files::isRegularFile ).toList() ) {
				assertThat( Files.readAllBytes( second.resolve( first.relativize( path ) ) ) )
						.isEqualTo( Files.readAllBytes( path ) );
			}
		}
		assertThatThrownBy( () -> processor.process( input, first, name -> true, false ) )
				.isInstanceOf( IllegalArgumentException.class ).hasMessageContaining( "empty" );
		assertThatThrownBy( () -> processor.process( input, input.resolve( "output" ), name -> true, false ) )
				.isInstanceOf( IllegalArgumentException.class ).hasMessageContaining( "disjoint" );
	}

	@Test
	void tckUsesOnlyPreparedClassesAndGeneratedFactory() throws Exception {
		String resource = TckHelper.class.getName().replace( '.', '/' ) + ".class";
		var locations = Collections.list( TckHelper.class.getClassLoader().getResources( resource ) );
		assertThat( locations ).hasSize( 1 );
		assertThat( locations.get( 0 ).getProtocol() ).isEqualTo( "file" );
		assertThat( TckHelper.factory().getClass() ).isEqualTo( tckgenerated.Factory.class );
	}
}
