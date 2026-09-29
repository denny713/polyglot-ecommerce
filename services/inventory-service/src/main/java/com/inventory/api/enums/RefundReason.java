package com.inventory.api.enums;

import lombok.Getter;

/** Why money paid for a sales order goes back to the customer. */
@Getter
public enum RefundReason implements Labeled {

    OVERPAYMENT("Overpayment"),
    CANCELLATION("Cancellation"),
    EXPIRED("Expired");

    private final String label;

    RefundReason(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
