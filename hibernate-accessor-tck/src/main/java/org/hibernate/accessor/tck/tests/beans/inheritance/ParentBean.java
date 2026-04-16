package org.hibernate.accessor.tck.tests.beans.inheritance;

public class ParentBean {

    private String parentField;

    public ParentBean() {
    }

    public String getParentField() {
        return parentField;
    }

    public void setParentField(String parentField) {
        this.parentField = parentField;
    }
}
