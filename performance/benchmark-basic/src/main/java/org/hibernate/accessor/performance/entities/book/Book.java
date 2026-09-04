/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.entities.book;

/**
 * Book branch of the {@link Order} graph: scalar properties plus cascades into {@link Author} and
 * {@link Publisher}. The deepest fan-out in the graph.
 */
public class Book {

	private String isbn;
	private String title;
	private int pageCount;
	private double price;
	private Author author;
	private Publisher publisher;

	public Book() {
	}

	public Book(String isbn, String title, int pageCount, double price, Author author, Publisher publisher) {
		this.isbn = isbn;
		this.title = title;
		this.pageCount = pageCount;
		this.price = price;
		this.author = author;
		this.publisher = publisher;
	}

	public String getIsbn() {
		return isbn;
	}

	public String getTitle() {
		return title;
	}

	public int getPageCount() {
		return pageCount;
	}

	public double getPrice() {
		return price;
	}

	public Author getAuthor() {
		return author;
	}

	public Publisher getPublisher() {
		return publisher;
	}
}
