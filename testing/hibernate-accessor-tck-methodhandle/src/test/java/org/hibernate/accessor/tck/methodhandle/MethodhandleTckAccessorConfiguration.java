package org.hibernate.accessor.tck.methodhandle;

import java.lang.invoke.MethodHandles;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.tck.util.TckAccessorConfiguration;

public class MethodhandleTckAccessorConfiguration implements TckAccessorConfiguration {
	@Override
	public AccessorFactory factory() {
		return AccessorFactory.methodHandle( MethodHandles.lookup() );
	}

}
