package org.hibernate.accessor.tck.tests.beans.visibility;

public final class Util {
    private Util() {
    }

    public static Object packagePrivateBeanInstance() {
        return new PackagePrivateBean();
    }
}
