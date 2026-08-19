/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.tck.generator;

import org.hibernate.accessor.HibernateAccessorFactory;
import org.hibernate.accessor.generator.runtime.AccessorImplFactory;
import org.hibernate.accessor.tck.util.TckAccessorConfiguration;

public class GeneratorTckAccessorConfiguration implements TckAccessorConfiguration {
	@Override
	public HibernateAccessorFactory factory() {
		try {
			String factoryFqcn = "org.hibernate.accessor.generator.generated.GeneratedHibernateAccessorFactory";
			String readerFqcn = "org.hibernate.accessor.generator.generated.GeneratedHibernateAccessorValueReaderImpl";
			String writerFqcn = "org.hibernate.accessor.generator.generated.GeneratedHibernateAccessorValueWriterImpl";
			String instantiatorFqcn = "org.hibernate.accessor.generator.generated.GeneratedHibernateAccessorInstantiatorImpl";

			AccessorImplFactory.init( readerFqcn, writerFqcn, instantiatorFqcn );

			ClassLoader cl = Thread.currentThread().getContextClassLoader();
			Class<?> factoryClass = cl.loadClass( factoryFqcn );
			return (HibernateAccessorFactory) factoryClass.getDeclaredConstructor().newInstance();
		}
		catch (Exception e) {
			throw new RuntimeException( "Failed to load generated accessor factory", e );
		}
	}
}
