package com.inventory.api.enums;

import lombok.Getter;

/** Lifecycle of a purchase order or purchase return. */
@Getter
public enum DocStatus implements Labeled {

    DRAFT("Draft"),
    APPROVED("Approved"),
    CANCELLED("Cancelled");

    private final String label;

    DocStatus(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
