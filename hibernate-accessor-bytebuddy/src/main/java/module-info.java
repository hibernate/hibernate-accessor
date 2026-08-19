module org.hibernate.accessor.bytebuddy {
	requires org.hibernate.accessor;
	requires net.bytebuddy;
	requires static org.jboss.logging;

	exports org.hibernate.accessor.bytebuddy;
	exports org.hibernate.accessor.bytebuddy.spi;
}
