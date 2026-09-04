/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.baseline;

import java.util.HashMap;
import java.util.Map;

import org.hibernate.accessor.HibernateAccessorValueReader;
import org.hibernate.accessor.performance.CascadeWalker.TypePlan;
import org.hibernate.accessor.performance.entities.book.Address;
import org.hibernate.accessor.performance.entities.book.Author;
import org.hibernate.accessor.performance.entities.book.Book;
import org.hibernate.accessor.performance.entities.book.Customer;
import org.hibernate.accessor.performance.entities.book.Order;
import org.hibernate.accessor.performance.entities.book.OrderLine;
import org.hibernate.accessor.performance.entities.book.Publisher;

/**
 * Hand-written {@link HibernateAccessorValueReader}s for the book-order graph, grouped into the same
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
				new HibernateAccessorValueReader<?>[] {
						new HibernateAccessorValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Order) o ).getId();
							}
						},
						new HibernateAccessorValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Order) o ).getOrderNumber();
							}
						},
						new HibernateAccessorValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Order) o ).getItemCount();
							}
						}
				},
				new HibernateAccessorValueReader<?>[] {
						new HibernateAccessorValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Order) o ).getCustomer();
							}
						},
						new HibernateAccessorValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Order) o ).getLines();
							}
						}
				} ) );

		plans.put( Customer.class, new TypePlan(
				new HibernateAccessorValueReader<?>[] {
						new HibernateAccessorValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Customer) o ).getId();
							}
						},
						new HibernateAccessorValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Customer) o ).getName();
							}
						},
						new HibernateAccessorValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Customer) o ).getEmail();
							}
						}
				},
				new HibernateAccessorValueReader<?>[] {
						new HibernateAccessorValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Customer) o ).getAddress();
							}
						}
				} ) );

		plans.put( Address.class, new TypePlan(
				new HibernateAccessorValueReader<?>[] {
						new HibernateAccessorValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Address) o ).getStreet();
							}
						},
						new HibernateAccessorValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Address) o ).getCity();
							}
						},
						new HibernateAccessorValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Address) o ).getPostalCode();
							}
						},
						new HibernateAccessorValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Address) o ).getCountry();
							}
						}
				},
				new HibernateAccessorValueReader<?>[0] ) );

		plans.put( OrderLine.class, new TypePlan(
				new HibernateAccessorValueReader<?>[] {
						new HibernateAccessorValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (OrderLine) o ).getQuantity();
							}
						},
						new HibernateAccessorValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (OrderLine) o ).getLineTotal();
							}
						}
				},
				new HibernateAccessorValueReader<?>[] {
						new HibernateAccessorValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (OrderLine) o ).getBook();
							}
						}
				} ) );

		plans.put( Book.class, new TypePlan(
				new HibernateAccessorValueReader<?>[] {
						new HibernateAccessorValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Book) o ).getIsbn();
							}
						},
						new HibernateAccessorValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Book) o ).getTitle();
							}
						},
						new HibernateAccessorValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Book) o ).getPageCount();
							}
						},
						new HibernateAccessorValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Book) o ).getPrice();
							}
						}
				},
				new HibernateAccessorValueReader<?>[] {
						new HibernateAccessorValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Book) o ).getAuthor();
							}
						},
						new HibernateAccessorValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Book) o ).getPublisher();
							}
						}
				} ) );

		plans.put( Author.class, new TypePlan(
				new HibernateAccessorValueReader<?>[] {
						new HibernateAccessorValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Author) o ).getName();
							}
						},
						new HibernateAccessorValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Author) o ).getBirthYear();
							}
						}
				},
				new HibernateAccessorValueReader<?>[0] ) );

		plans.put( Publisher.class, new TypePlan(
				new HibernateAccessorValueReader<?>[] {
						new HibernateAccessorValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Publisher) o ).getName();
							}
						},
						new HibernateAccessorValueReader<Object>() {
							@Override
							public Object get(Object o) {
								return ( (Publisher) o ).getFoundedYear();
							}
						}
				},
				new HibernateAccessorValueReader<?>[0] ) );

		return plans;
	}
}
