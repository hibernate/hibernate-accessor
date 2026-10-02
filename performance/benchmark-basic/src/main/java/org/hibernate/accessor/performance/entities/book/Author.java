package org.hibernate.accessor.performance.entities.book;

/** Leaf of the book branch of the {@link Order} graph: all scalar properties, no cascades. */
public class Author {

	private String name;
	private int birthYear;

	public Author() {
	}

	public Author(String name, int birthYear) {
		this.name = name;
		this.birthYear = birthYear;
	}

	public String getName() {
		return name;
	}

	public int getBirthYear() {
		return birthYear;
	}
}
