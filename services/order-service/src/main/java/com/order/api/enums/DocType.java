package com.order.api.enums;

import lombok.Getter;

/**
 * The document types the {@code generate_doc_no} database function numbers. The label
 * is the prefix of the number and must match the function's list of types.
 */
@Getter
public enum DocType implements Labeled {

    PURCHASE_ORDER("PO"),
    PURCHASE_RETURN("PR"),
    SALES_ORDER("SO"),
    PAYMENT("PY"),
    REFUND("RF");

    private final String label;

    DocType(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
