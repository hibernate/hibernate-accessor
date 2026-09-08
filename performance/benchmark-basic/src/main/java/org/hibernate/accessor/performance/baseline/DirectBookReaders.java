/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.baseline;

import java.util.HashMap;
import java.util.Map;

import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.performance.CascadeWalker.TypePlan;
import org.hibernate.accessor.performance.entities.book.Address;
import org.hibernate.accessor.performance.entities.book.Author;
import org.hibernate.accessor.performance.entities.book.Book;
import org.hibernate.accessor.performance.entities.book.Customer;
import org.hibernate.accessor.performance.entities.book.Order;
import org.hibernate.accessor.performance.entities.book.OrderLine;
import org.hibernate.accessor.performance.entities.book.Publisher;

/**
 * Hand-written {@link ValueReader}s for the book-order graph, grouped into the same
 * per-type {@link TypePlan}s a strategy produces. Used by the {@code iface} cascade baseline.
 *
 * <p>These are explicit anonymous classes rather than lambdas on purpose: a lambda here would route
 * through {@code LambdaMetafactory} and quietly measure the lambda strategy instead of a hand-written
 * reference. Every reader reads through the getter (the only form available for the {@code private},
 * cross-package entity fields).
 */
public final class DirectBookReaders {

	private DirectBookReaders() {
	}

	/** One {@link TypePlan} per entity type, matching {@code BookSchema}'s scalar/cascade split. */
	public static Map<Class<?>, TypePlan> plans() {
		Map<Class<?>, TypePlan> plans = new HashMap<>();

		plans.put( Order.class, new TypePlan(
				new ValueReader<?>[] {
						new ValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Order) o ).getId();
							}
						},
						new ValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Order) o ).getOrderNumber();
							}
						},
						new ValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Order) o ).getItemCount();
							}
						}
				},
				new ValueReader<?>[] {
						new ValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Order) o ).getCustomer();
							}
						},
						new ValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Order) o ).getLines();
							}
						}
				} ) );

		plans.put( Customer.class, new TypePlan(
				new ValueReader<?>[] {
						new ValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Customer) o ).getId();
							}
						},
						new ValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Customer) o ).getName();
							}
						},
						new ValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Customer) o ).getEmail();
							}
						}
				},
				new ValueReader<?>[] {
						new ValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Customer) o ).getAddress();
							}
						}
				} ) );

		plans.put( Address.class, new TypePlan(
				new ValueReader<?>[] {
						new ValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Address) o ).getStreet();
							}
						},
						new ValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Address) o ).getCity();
							}
						},
						new ValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Address) o ).getPostalCode();
							}
						},
						new ValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Address) o ).getCountry();
							}
						}
				},
				new ValueReader<?>[0] ) );

		plans.put( OrderLine.class, new TypePlan(
				new ValueReader<?>[] {
						new ValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (OrderLine) o ).getQuantity();
							}
						},
						new ValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (OrderLine) o ).getLineTotal();
							}
						}
				},
				new ValueReader<?>[] {
						new ValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (OrderLine) o ).getBook();
							}
						}
				} ) );

		plans.put( Book.class, new TypePlan(
				new ValueReader<?>[] {
						new ValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Book) o ).getIsbn();
							}
						},
						new ValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Book) o ).getTitle();
							}
						},
						new ValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Book) o ).getPageCount();
							}
						},
						new ValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Book) o ).getPrice();
							}
						}
				},
				new ValueReader<?>[] {
						new ValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Book) o ).getAuthor();
							}
						},
						new ValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Book) o ).getPublisher();
							}
						}
				} ) );

		plans.put( Author.class, new TypePlan(
				new ValueReader<?>[] {
						new ValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Author) o ).getName();
							}
						},
						new ValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Author) o ).getBirthYear();
							}
						}
				},
				new ValueReader<?>[0] ) );

		plans.put( Publisher.class, new TypePlan(
				new ValueReader<?>[] {
						new ValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Publisher) o ).getName();
							}
						},
						new ValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Publisher) o ).getFoundedYear();
							}
						}
				},
				new ValueReader<?>[0] ) );

		return plans;
	}
}
