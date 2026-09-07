package com.inventory.api.enums;

import lombok.Getter;

/**
 * Lifecycle of a purchase order or purchase return.
 * <p>
 * The services, not this enum, enforce the transitions: only a {@code DRAFT} may be
 * approved or cancelled, an {@code APPROVED} document is final, and a
 * {@code CANCELLED} one returns to {@code DRAFT} when it is submitted again.
 * {@code APPROVED} is also the point where stock moves.
 */
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
