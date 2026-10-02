module org.hibernate.accessor {
	requires static org.jboss.logging;
	requires static org.jboss.logging.annotations;
	requires static io.smallrye.classfile;

	exports org.hibernate.accessor;
	exports org.hibernate.accessor.spi;
	exports org.hibernate.accessor.classfile.spi;
}
