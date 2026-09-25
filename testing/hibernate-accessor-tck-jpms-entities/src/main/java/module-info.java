module org.hibernate.accessor.tck.jpms.entities {
	exports org.hibernate.accessor.tck.jpms.entities;
	opens org.hibernate.accessor.tck.jpms.entities;
	opens org.hibernate.accessor.tck.jpms.asm to org.hibernate.accessor.tck.jpms;
	opens org.hibernate.accessor.tck.jpms.bytebuddy to org.hibernate.accessor.tck.jpms;
	opens org.hibernate.accessor.tck.jpms.classfile to org.hibernate.accessor.tck.jpms;
}
