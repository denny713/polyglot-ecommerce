package com.inventory.api.enums;

import lombok.Getter;

/** Which kind of document a number or stock movement belongs to. */
@Getter
public enum DocType implements Labeled {

    SO("Sales Order"),
    PO("Purchase Order"),
    PR("Purchase Return");

    private final String label;

    DocType(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
