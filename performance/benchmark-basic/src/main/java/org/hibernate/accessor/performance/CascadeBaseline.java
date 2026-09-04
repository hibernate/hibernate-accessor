/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance;

import java.util.concurrent.TimeUnit;

import org.hibernate.accessor.performance.baseline.BookSwitchWalker;
import org.hibernate.accessor.performance.baseline.DirectBookReaders;
import org.hibernate.accessor.performance.entities.book.Address;
import org.hibernate.accessor.performance.entities.book.Author;
import org.hibernate.accessor.performance.entities.book.Book;
import org.hibernate.accessor.performance.entities.book.BookGraph;
import org.hibernate.accessor.performance.entities.book.Customer;
import org.hibernate.accessor.performance.entities.book.Order;
import org.hibernate.accessor.performance.entities.book.OrderLine;
import org.hibernate.accessor.performance.entities.book.Publisher;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Hand-written reference points for {@link CascadeBenchmark}.
 *
 * <ul>
 *   <li><b>raw</b> -- the graph is walked with the property getters called directly (no interface, no
 *       per-type dispatch map); primitives stay unboxed. The absolute floor the JIT fully inlines.</li>
 *   <li><b>iface</b> -- the same {@link CascadeWalker} the strategies use, fed hand-written
 *       {@link org.hibernate.accessor.HibernateAccessorValueReader} implementations, so the call shape
 *       (and megamorphism) matches and the delta against a strategy isolates that strategy's internals.</li>
 *   <li><b>switch</b> -- the "build-time two-layer switch dispatcher" idea: every read funnels through a
 *       single monomorphic {@link org.hibernate.accessor.performance.baseline.BookSwitchDispatcher#get}
 *       call that resolves the type and member with two nested {@code switch}es, instead of a megamorphic
 *       virtual call to a per-member/per-type accessor. Getter-based, so comparable to {@code iface}.</li>
 * </ul>
 *
 * <p>Both references read through getters: the entity fields are {@code private} to another package, so a
 * hand-written class cannot read them directly -- the getter is the hand-written floor for such members.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class CascadeBaseline {

	@Param("8")
	private int lineCount;

	private Order order;
	private CascadeWalker walker;
	private BookSwitchWalker switchWalker;

	@Setup
	public void setUp() {
		this.order = BookGraph.sampleOrder( lineCount );
		this.walker = new CascadeWalker( DirectBookReaders.plans() );
		this.switchWalker = BookSwitchWalker.create();
	}

	@Benchmark
	public long rawCascade() {
		long acc = 0;
		Order o = order;
		acc += Long.hashCode( o.getId() );
		acc += o.getOrderNumber().hashCode();
		acc += Integer.hashCode( o.getItemCount() );

		Customer c = o.getCustomer();
		acc += Long.hashCode( c.getId() );
		acc += c.getName().hashCode();
		acc += c.getEmail().hashCode();

		Address a = c.getAddress();
		acc += a.getStreet().hashCode();
		acc += a.getCity().hashCode();
		acc += a.getPostalCode().hashCode();
		acc += a.getCountry().hashCode();

		for ( OrderLine line : o.getLines() ) {
			acc += Integer.hashCode( line.getQuantity() );
			acc += Double.hashCode( line.getLineTotal() );

			Book b = line.getBook();
			acc += b.getIsbn().hashCode();
			acc += b.getTitle().hashCode();
			acc += Integer.hashCode( b.getPageCount() );
			acc += Double.hashCode( b.getPrice() );

			Author author = b.getAuthor();
			acc += author.getName().hashCode();
			acc += Integer.hashCode( author.getBirthYear() );

			Publisher publisher = b.getPublisher();
			acc += publisher.getName().hashCode();
			acc += Integer.hashCode( publisher.getFoundedYear() );
		}
		return acc;
	}

	@Benchmark
	public long ifaceCascade() {
		return walker.walk( order );
	}

	@Benchmark
	public long switchCascade() {
		return switchWalker.walk( order );
	}
}
