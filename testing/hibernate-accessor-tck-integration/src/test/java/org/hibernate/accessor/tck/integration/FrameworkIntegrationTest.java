/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Hibernate Authors. See AUTHORS.txt.
 */
package org.hibernate.accessor.tck.integration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class FrameworkIntegrationTest {
	@TempDir
	Path directory;

	static Stream<Arguments> deployments() {
		return Stream.of( "reflection", "methodhandle", "lambda", "asm", "bytebuddy", "classfile" )
				.flatMap( strategy -> Stream.of(
						Arguments.of( strategy, "classpath", false ),
						Arguments.of( strategy, "modulepath", false ),
						Arguments.of( strategy, "mixed", false ),
						Arguments.of( strategy, "modulepath", true )
				) );
	}

	@ParameterizedTest(name = "{0}: {1}, closed={2}")
	@MethodSource("deployments")
	void frameworkLookupControlsAccess(String strategy, String placement, boolean closed) throws Exception {
		String dependencies = System.getProperty( "accessor.runtime.path" );
		Path entitySource = source( "entity/Entity.java", "Entity.java" );
		Path frameworkSource = source( "framework/Framework.java", "Framework.java" );
		Path entities = directory.resolve( "entities" );
		Path framework = directory.resolve( "framework" );
		boolean modular = !placement.equals( "classpath" );
		boolean namedEntity = placement.equals( "modulepath" );
		List<String> entityCompile = new ArrayList<>( List.of( "-d", entities.toString(), entitySource.toString() ) );
		if ( namedEntity ) {
			Path descriptor = entitySource.resolveSibling( "module-info.java" );
			Files.writeString( descriptor, "module integration.entities { "
					+ (closed ? "" : "opens entities to integration.framework;") + " }" );
			entityCompile.add( descriptor.toString() );
		}
		compile( entityCompile );
		List<String> frameworkCompile = new ArrayList<>( List.of(
				"-d", framework.toString(), modular ? "--module-path" : "--class-path", dependencies,
				frameworkSource.toString()
		) );
		if ( modular ) {
			Path descriptor = frameworkSource.resolveSibling( "module-info.java" );
			Files.writeString( descriptor, """
					module integration.framework {
					    requires org.hibernate.accessor;
					    requires org.hibernate.accessor.asm;
					    requires org.hibernate.accessor.bytebuddy;
					}
					""" );
			frameworkCompile.add( descriptor.toString() );
		}
		compile( frameworkCompile );
		List<String> command = new ArrayList<>( List.of(
				Path.of( System.getProperty( "java.home" ), "bin", "java" ).toString(), "-Xverify:all"
		) );
		if ( modular ) {
			command.addAll( List.of( "--module-path", dependencies + File.pathSeparator + framework
					+ (namedEntity ? File.pathSeparator + entities : ""), "--add-modules", "ALL-MODULE-PATH" ) );
			if ( !namedEntity ) {
				command.addAll( List.of( "--class-path", entities.toString() ) );
			}
			command.addAll( List.of( "--module", "integration.framework/framework.Framework" ) );
		}
		else {
			command.addAll( List.of( "--class-path", dependencies + File.pathSeparator + framework
					+ File.pathSeparator + entities, "framework.Framework" ) );
		}
		command.addAll( List.of( strategy, placement, Boolean.toString( closed ) ) );
		// Each case gets a fresh JVM: read edges, openings and injected bridges cannot leak between cases.
		Path output = directory.resolve( "process.log" );
		Process process = new ProcessBuilder( command ).redirectErrorStream( true ).redirectOutput( output.toFile() ).start();
		try {
			assertThat( process.waitFor( 30, TimeUnit.SECONDS ) ).as( "integration process completed" ).isTrue();
			assertThat( process.exitValue() ).withFailMessage( Files.readString( output ) ).isZero();
			assertThat( Files.readString( output ) ).contains( "PASS:" );
		}
		finally {
			process.destroyForcibly();
		}
	}

	private Path source(String destination, String resource) throws IOException {
		Path file = directory.resolve( "src" ).resolve( destination );
		Files.createDirectories( file.getParent() );
		try ( var input = getClass().getResourceAsStream( "/fixtures/" + resource ) ) {
			Files.copy( input, file );
		}
		return file;
	}

	private static void compile(List<String> arguments) {
		assertThat( ToolProvider.getSystemJavaCompiler().run( null, null, null, arguments.toArray( String[]::new ) ) )
				.as( "fixture compilation" ).isZero();
	}
}
