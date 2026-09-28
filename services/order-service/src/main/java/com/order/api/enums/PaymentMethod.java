package com.order.api.enums;

import lombok.Getter;

@Getter
public enum PaymentMethod implements Labeled {

    TRF("Transfer"),
    VA("Virtual Account"),
    EWL("E-Wallet");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
