/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.performance.entities.mega;

import java.util.List;

/** The set of distinct entity types available for the megamorphic benchmark. */
public final class MegaEntities {

	public static final List<Class<?>> CLASSES = List.of(
			Entity00.class,
			Entity01.class,
			Entity02.class,
			Entity03.class,
			Entity04.class,
			Entity05.class,
			Entity06.class,
			Entity07.class,
			Entity08.class,
			Entity09.class,
			Entity10.class,
			Entity11.class
	);

	private MegaEntities() {
	}
}
