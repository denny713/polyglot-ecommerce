package com.inventory.api.enums;

import lombok.Getter;

/**
 * Lifecycle of a sales order.
 * <p>
 * None of these transitions are decided here. A sales order is placed, paid for and
 * closed in the order service, and inventory only ever sees the status the document
 * arrived with. {@code PAID} is the one that matters, because that is the point at
 * which the goods are considered sold and the stock is moved.
 * <p>
 * The rest are carried so a stored order reads the same as it does upstream:
 * {@code PENDING} is still awaiting payment, {@code EXPIRED} and {@code CANCELLED}
 * are the two ways an order stops short of it, and {@code COMPLETED} is an order
 * already handed over to the customer.
 */
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
