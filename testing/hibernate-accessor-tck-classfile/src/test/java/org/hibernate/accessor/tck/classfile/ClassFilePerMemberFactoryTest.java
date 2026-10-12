package org.hibernate.accessor.tck.classfile;

import java.lang.invoke.MethodHandles;
import java.util.Map;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.classfile.ClassFileAccessorConfiguration;
import org.hibernate.accessor.classfile.ClassFileGenerationStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClassFilePerMemberFactoryTest {
	@Test
	void generatesAndCachesDedicatedAccessors() throws Exception {
		for ( Object strategy : new Object[] { ClassFileGenerationStrategy.PER_MEMBER, "PER_MEMBER" } ) {
			var factory = AccessorFactory.classFile( new ClassFileAccessorConfiguration(
					MethodHandles.lookup(), Map.of( ClassFileAccessorConfiguration.GENERATION_STRATEGY, strategy ) ) );
			var field = Entity.class.getDeclaredField( "value" );
			var getter = Entity.class.getDeclaredMethod( "getValue" );
			var setter = Entity.class.getDeclaredMethod( "setValue", long.class );
			var entity = new Entity();
			var fieldReader = factory.valueReader( field );
			var fieldWriter = factory.valueWriter( field );
			var methodReader = factory.valueReader( getter );
			var methodWriter = factory.valueWriter( setter );
			assertTrue( fieldReader.getClass().isHidden() );
			assertTrue( fieldWriter.getClass().isHidden() );
			assertTrue( methodReader.getClass().isHidden() );
			assertTrue( methodWriter.getClass().isHidden() );
			assertSame( fieldReader, factory.valueReader( field ) );
			assertSame( fieldWriter, factory.valueWriter( field ) );
			assertSame( methodReader, factory.valueReader( getter ) );
			assertSame( methodWriter, factory.valueWriter( setter ) );
			fieldWriter.set( entity, 12 );
			assertEquals( 12L, methodReader.get( entity ) );
			methodWriter.set( entity, 34 );
			assertEquals( 34L, fieldReader.get( entity ) );
		}
	}

	private static class Entity {
		private long value;
		private long getValue() { return value; }
		private long setValue(long value) { this.value = value; return value; }
	}
}
