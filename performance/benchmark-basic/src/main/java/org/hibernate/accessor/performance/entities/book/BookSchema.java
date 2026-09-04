/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.entities.book;

import java.util.List;
import java.util.Map;

/**
 * Declarative metamodel of the book-order graph: for each entity type, the properties a cascading
 * traversal (validator / ORM dirty-check) would visit.
 *
 * <p>Each {@link Member} records both its field name and its getter name so a traversal can be wired
 * through either access kind, and a {@code cascade} flag marking the reference/collection edges the
 * walk recurses into (as opposed to the scalar leaves it merely reads).
 */
public final class BookSchema {

	private BookSchema() {
	}

	/** A single property of an entity, addressable by field or by getter. */
	public record Member(String field, String getter, boolean cascade) {

		static Member scalar(String field, String getter) {
			return new Member( field, getter, false );
		}

		static Member cascade(String field, String getter) {
			return new Member( field, getter, true );
		}
	}

	/** The entity types in the graph, root first. */
	public static final List<Class<?>> ENTITIES = List.of(
			Order.class,
			Customer.class,
			Address.class,
			OrderLine.class,
			Book.class,
			Author.class,
			Publisher.class
	);

	/** Per-type property lists, in declaration order. */
	public static final Map<Class<?>, List<Member>> SCHEMA = Map.of(
			Order.class, List.of(
					Member.scalar( "id", "getId" ),
					Member.scalar( "orderNumber", "getOrderNumber" ),
					Member.scalar( "itemCount", "getItemCount" ),
					Member.cascade( "customer", "getCustomer" ),
					Member.cascade( "lines", "getLines" ) ),
			Customer.class, List.of(
					Member.scalar( "id", "getId" ),
					Member.scalar( "name", "getName" ),
					Member.scalar( "email", "getEmail" ),
					Member.cascade( "address", "getAddress" ) ),
			Address.class, List.of(
					Member.scalar( "street", "getStreet" ),
					Member.scalar( "city", "getCity" ),
					Member.scalar( "postalCode", "getPostalCode" ),
					Member.scalar( "country", "getCountry" ) ),
			OrderLine.class, List.of(
					Member.scalar( "quantity", "getQuantity" ),
					Member.scalar( "lineTotal", "getLineTotal" ),
					Member.cascade( "book", "getBook" ) ),
			Book.class, List.of(
					Member.scalar( "isbn", "getIsbn" ),
					Member.scalar( "title", "getTitle" ),
					Member.scalar( "pageCount", "getPageCount" ),
					Member.scalar( "price", "getPrice" ),
					Member.cascade( "author", "getAuthor" ),
					Member.cascade( "publisher", "getPublisher" ) ),
			Author.class, List.of(
					Member.scalar( "name", "getName" ),
					Member.scalar( "birthYear", "getBirthYear" ) ),
			Publisher.class, List.of(
					Member.scalar( "name", "getName" ),
					Member.scalar( "foundedYear", "getFoundedYear" ) )
	);
}
