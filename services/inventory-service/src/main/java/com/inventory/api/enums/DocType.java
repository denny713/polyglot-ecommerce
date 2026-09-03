package com.inventory.api.enums;

import lombok.Getter;

@Getter
public enum DocType implements Labeled {

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
