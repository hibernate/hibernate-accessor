module org.hibernate.accessor.asm {
	requires org.hibernate.accessor;
	requires org.objectweb.asm;
	requires org.jboss.logging;

	exports org.hibernate.accessor.asm;
	exports org.hibernate.accessor.asm.spi;
	exports org.hibernate.accessor.asm.aot;
}
