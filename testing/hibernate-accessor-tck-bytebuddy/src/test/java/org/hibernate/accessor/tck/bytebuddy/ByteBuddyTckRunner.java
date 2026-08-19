package org.hibernate.accessor.tck.bytebuddy;

import org.junit.platform.suite.api.IncludeClassNamePatterns;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SuiteDisplayName("ByteBuddy TCK tests Runner")
@SelectPackages("org.hibernate.accessor.tck")
@IncludeClassNamePatterns({".*Test"})
public class ByteBuddyTckRunner {
}
