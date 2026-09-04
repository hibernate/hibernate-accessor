/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.entities.book;

import java.util.ArrayList;
import java.util.List;

/** Builds a populated {@link Order} graph for the cascade traversal benchmarks. */
public final class BookGraph {

	private BookGraph() {
	}

	/**
	 * A realistic order: one customer with an address, and {@code lineCount} lines, each pointing at a
	 * distinct book with its own author and publisher. The traversal visits every reachable property.
	 *
	 * @param lineCount how many order lines (and therefore books/authors/publishers) to create
	 */
	public static Order sampleOrder(int lineCount) {
		Address address = new Address( "742 Evergreen Terrace", "Springfield", "49007", "USA" );
		Customer customer = new Customer( 1001L, "Ada Lovelace", "ada@example.com", address );

		List<OrderLine> lines = new ArrayList<>( lineCount );
		for ( int i = 0; i < lineCount; i++ ) {
			Author author = new Author( "Author " + i, 1900 + i );
			Publisher publisher = new Publisher( "Publisher " + i, 1850 + i );
			Book book = new Book(
					"978-0-00-" + String.format( "%06d", i ) + "-0",
					"Book Title " + i,
					100 + i,
					9.99 + i,
					author,
					publisher );
			lines.add( new OrderLine( 1 + i, ( 9.99 + i ) * ( 1 + i ), book ) );
		}

		return new Order( 5000L, "ORD-5000", lineCount, customer, lines );
	}
}
