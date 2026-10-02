package org.hibernate.accessor.tck.tests.crossclassloader;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.ValueWriter;
import org.hibernate.accessor.tck.tests.beans.LifecycleBean;
import org.hibernate.accessor.tck.util.TckHelper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class PerMemberAccessorLifetimeTest {

	@Test
	void liveAccessorsRemainCachedAndFunctional() throws Exception {
		var factory = perMemberFactory();
		for ( int kind = 0; kind < 4; kind++ ) {
			Object accessor = accessor( factory, kind );
			var reference = new WeakReference<>( accessor );
			System.gc();
			assertNotNull( reference.get() );
			assertSame( accessor, accessor( factory, kind ), "live accessor must remain cached" );
			exercise( accessor );
			Reference.reachabilityFence( accessor );
		}
	}

	@Test
	void releasedAccessorsUnloadWhileFactoryAndEntitySurvive() throws Exception {
		var factory = perMemberFactory();
		for ( int kind = 0; kind < 4; kind++ ) {
			awaitCollection( releasedAccessor( factory, kind ) );
			Object replacement = accessor( factory, kind );
			exercise( replacement );
			assertSame( replacement, accessor( factory, kind ) );
		}
		Reference.reachabilityFence( factory );
		Reference.reachabilityFence( LifecycleBean.class );
	}

	@Test
	void discardedFactoriesDoNotRetainAccessorClassesOnLiveEntity() throws Exception {
		// Check the strategy before allocating the disposable factories.
		perMemberFactory();
		var references = new ArrayList<WeakReference<?>>();
		for ( int i = 0; i < 8; i++ ) {
			addDiscardedFactoryReferences( references );
		}
		awaitCollection( references );
		Reference.reachabilityFence( LifecycleBean.class );
	}

	@Test
	void concurrentRequestsReuseEachLiveAccessor() throws Exception {
		var factory = perMemberFactory();
		var executor = Executors.newFixedThreadPool( 8 );
		try {
			for ( int kind = 0; kind < 4; kind++ ) {
				final int accessorKind = kind;
				var start = new CountDownLatch( 1 );
				var results = new ArrayList<Future<Object>>();
				for ( int i = 0; i < 32; i++ ) {
					results.add( executor.submit( () -> {
						assertTrue( start.await( 10, TimeUnit.SECONDS ) );
						return accessor( factory, accessorKind );
					} ) );
				}
				start.countDown();
				Object first = results.get( 0 ).get( 10, TimeUnit.SECONDS );
				for ( var result : results ) {
					assertSame( first, result.get( 10, TimeUnit.SECONDS ) );
				}
				exercise( first );
			}
		}
		finally {
			executor.shutdownNow();
		}
	}

	private static AccessorFactory perMemberFactory() {
		var factory = TckHelper.factory();
		assumeTrue( factory.getClass().getName().equals( "org.hibernate.accessor.asm.impl.AsmPerMemberAccessorFactory" ) );
		return factory;
	}

	private static Object accessor(AccessorFactory factory, int kind) throws Exception {
		Object accessor = switch ( kind ) {
			case 0 -> factory.valueReader( LifecycleBean.class.getField( "value" ) );
			case 1 -> factory.valueWriter( LifecycleBean.class.getField( "value" ) );
			case 2 -> factory.valueReader( LifecycleBean.class.getMethod( "getValue" ) );
			case 3 -> factory.valueWriter( LifecycleBean.class.getMethod( "setValue", String.class ) );
			default -> throw new AssertionError( kind );
		};
		assertTrue( accessor.getClass().isHidden(), "must exercise generated accessors rather than reflection fallback" );
		return accessor;
	}

	private static void exercise(Object accessor) {
		var bean = new LifecycleBean();
		bean.value = "read";
		if ( accessor instanceof ValueReader<?> reader ) {
			assertEquals( "read", reader.get( bean ) );
		}
		else if ( accessor instanceof ValueWriter writer ) {
			writer.set( bean, "written" );
			assertEquals( "written", bean.value );
		}
		else {
			throw new AssertionError( accessor );
		}
	}

	private static List<WeakReference<?>> releasedAccessor(AccessorFactory factory, int kind) throws Exception {
		Object accessor = accessor( factory, kind );
		exercise( accessor );
		return List.of( new WeakReference<>( accessor ), new WeakReference<>( accessor.getClass() ) );
	}

	private static void addDiscardedFactoryReferences(List<WeakReference<?>> references) throws Exception {
		var factory = perMemberFactory();
		references.add( new WeakReference<>( factory ) );
		for ( int kind = 0; kind < 4; kind++ ) {
			references.addAll( releasedAccessor( factory, kind ) );
		}
	}

	private static void awaitCollection(List<WeakReference<?>> references) throws Exception {
		for ( int attempt = 0; attempt < 100 && references.stream().anyMatch( r -> r.get() != null ); attempt++ ) {
			System.gc();
			Thread.sleep( 25 );
		}
		for ( var reference : references ) {
			assertNull( reference.get(), "accessor, hidden class, or factory still reachable after repeated full-GC requests" );
		}
	}
}
