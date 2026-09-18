package com.order.api.enums;

import lombok.Getter;

/** Lifecycle of a sales order. */
@Getter
public enum SalesStatus implements Labeled {

    PENDING("Pending"),
    EXPIRED("Expired"),
    PAID("Paid"),
    CANCELLED("Cancelled"),
    COMPLETED("Completed");

    private final String label;

    SalesStatus(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
