package org.hibernate.accessor.tck.classfile;

import org.junit.platform.suite.api.IncludeClassNamePatterns;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SuiteDisplayName("ClassFile API TCK tests Runner")
@SelectPackages("org.hibernate.accessor.tck")
@IncludeClassNamePatterns({".*Test"})
public class ClassFileTckRunner {
}
