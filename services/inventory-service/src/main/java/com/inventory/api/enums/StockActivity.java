package com.inventory.api.enums;

import lombok.Getter;

@Getter
public enum StockActivity implements Labeled {

    SI("Stock In"),
    SO("Stock Out");

    private final String label;

    StockActivity(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
