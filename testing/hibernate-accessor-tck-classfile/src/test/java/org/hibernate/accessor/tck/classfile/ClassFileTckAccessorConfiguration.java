package org.hibernate.accessor.tck.classfile;

import java.lang.invoke.MethodHandles;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.tck.util.TckAccessorConfiguration;

public class ClassFileTckAccessorConfiguration implements TckAccessorConfiguration {

	@Override
	public AccessorFactory factory() {
		return AccessorFactory.classFile( MethodHandles.lookup() );
	}
}
