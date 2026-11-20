package org.hibernate.accessor.tck.classfile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.classfile.ClassFileAccessorConfiguration;
import org.hibernate.accessor.spi.AccessorConfiguration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClassFileFactoryTest {
	@Test
	void invalidGenerationStrategyThrowsIllegalArgumentException() {
		var configuration = new AccessorConfiguration( MethodHandles.lookup(),
				Map.of( ClassFileAccessorConfiguration.GENERATION_STRATEGY, "INVALID" ) );

		assertThrowsExactly( IllegalArgumentException.class, () -> AccessorFactory.classFile( configuration ) );
	}

	@Test
	void crossClassWriterDumpsUnderClassFilePackage(@TempDir Path dumpDir) throws Exception {
		var configuration = new AccessorConfiguration( MethodHandles.lookup(),
				Map.of( AccessorConfiguration.DUMP_BYTECODE_DIR, dumpDir.toString() ) );
		var factory = AccessorFactory.classFile( configuration );

		factory.multiValueWriter( Child.class, Parent.class.getDeclaredField( "first" ),
				Child.class.getDeclaredField( "second" ) );

		assertTrue( Files.isRegularFile( dumpDir.resolve(
				"org/hibernate/accessor/classfile/impl/HibernateAccessorMultiBulkWriter.class" ) ) );
		assertFalse( Files.exists( dumpDir.resolve(
				"org/hibernate/accessor/asm/impl/HibernateAccessorMultiBulkWriter.class" ) ) );
	}

	private static class Parent {
		private int first;
	}

	private static class Child extends Parent {
		private int second;
	}
}
