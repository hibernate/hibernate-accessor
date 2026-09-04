/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.baseline;

import org.hibernate.accessor.performance.entities.book.Address;
import org.hibernate.accessor.performance.entities.book.Author;
import org.hibernate.accessor.performance.entities.book.Book;
import org.hibernate.accessor.performance.entities.book.Customer;
import org.hibernate.accessor.performance.entities.book.Order;
import org.hibernate.accessor.performance.entities.book.OrderLine;
import org.hibernate.accessor.performance.entities.book.Publisher;

/**
 * Hand-written realization of the "build-time two-layer switch dispatcher" idea: instead of one
 * reader object per member (megamorphic at the walker's {@code reader.get} call site) or one bulk
 * accessor per type (megamorphic at the inner {@code readByX} call), <em>every</em> read in the whole
 * graph funnels through this single {@code static} method. The call site therefore sees exactly one
 * target and stays monomorphic; the polymorphism is resolved by two nested {@code tableswitch}es
 * (first on the entity type, then on the member) that the JIT handles as cheap branches rather than
 * virtual dispatch.
 *
 * <p>This is the best-case shape a build-time code generator could emit. It reads through getters
 * (the entity fields are {@code private} to another package), so it is the getter/method-access
 * reference point, comparable to {@code ifaceCascade} and the {@code METHOD} strategy column.
 *
 * <p>Class ids follow {@code BookSchema.ENTITIES} order; member ids follow each type's declaration
 * order in {@code BookSchema.SCHEMA}. {@link BookSwitchWalker} builds its plans against the same
 * numbering.
 */
public final class BookSwitchDispatcher {

	private BookSwitchDispatcher() {
	}

	static final int ORDER = 0;
	static final int CUSTOMER = 1;
	static final int ADDRESS = 2;
	static final int ORDER_LINE = 3;
	static final int BOOK = 4;
	static final int AUTHOR = 5;
	static final int PUBLISHER = 6;

	/**
	 * Reads member {@code memberId} of the given {@code bean}, whose entity type is identified by
	 * {@code classId}. Scalars are returned boxed (matching the {@code HibernateAccessorValueReader}
	 * contract the other strategies satisfy); reference/collection edges are returned as-is.
	 */
	public static Object get(int classId, int memberId, Object bean) {
		switch ( classId ) {
			case ORDER: {
				Order o = (Order) bean;
				return switch ( memberId ) {
					case 0 -> o.getId();
					case 1 -> o.getOrderNumber();
					case 2 -> o.getItemCount();
					case 3 -> o.getCustomer();
					case 4 -> o.getLines();
					default -> null;
				};
			}
			case CUSTOMER: {
				Customer c = (Customer) bean;
				return switch ( memberId ) {
					case 0 -> c.getId();
					case 1 -> c.getName();
					case 2 -> c.getEmail();
					case 3 -> c.getAddress();
					default -> null;
				};
			}
			case ADDRESS: {
				Address a = (Address) bean;
				return switch ( memberId ) {
					case 0 -> a.getStreet();
					case 1 -> a.getCity();
					case 2 -> a.getPostalCode();
					case 3 -> a.getCountry();
					default -> null;
				};
			}
			case ORDER_LINE: {
				OrderLine l = (OrderLine) bean;
				return switch ( memberId ) {
					case 0 -> l.getQuantity();
					case 1 -> l.getLineTotal();
					case 2 -> l.getBook();
					default -> null;
				};
			}
			case BOOK: {
				Book b = (Book) bean;
				return switch ( memberId ) {
					case 0 -> b.getIsbn();
					case 1 -> b.getTitle();
					case 2 -> b.getPageCount();
					case 3 -> b.getPrice();
					case 4 -> b.getAuthor();
					case 5 -> b.getPublisher();
					default -> null;
				};
			}
			case AUTHOR: {
				Author a = (Author) bean;
				return switch ( memberId ) {
					case 0 -> a.getName();
					case 1 -> a.getBirthYear();
					default -> null;
				};
			}
			case PUBLISHER: {
				Publisher p = (Publisher) bean;
				return switch ( memberId ) {
					case 0 -> p.getName();
					case 1 -> p.getFoundedYear();
					default -> null;
				};
			}
			default:
				return null;
		}
	}
}
