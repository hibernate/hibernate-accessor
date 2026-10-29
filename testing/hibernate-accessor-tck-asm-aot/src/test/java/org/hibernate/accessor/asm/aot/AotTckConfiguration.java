package org.hibernate.accessor.asm.aot;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.tck.util.TckAccessorConfiguration;

public final class AotTckConfiguration implements TckAccessorConfiguration {
	@Override
	public AccessorFactory factory() {
		return tckgenerated.Factory.create();
	}
}
