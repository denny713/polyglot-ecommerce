package com.inventory.api.enums;

import lombok.Getter;

/** Direction of a stock movement: {@code SI} adds to the position, {@code SO} subtracts from it. */
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
