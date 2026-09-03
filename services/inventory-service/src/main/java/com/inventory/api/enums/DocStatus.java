package com.inventory.api.enums;

import lombok.Getter;

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
