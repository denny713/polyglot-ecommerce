package com.order.api.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Pins the labels of the sales order lifecycle. */
class LabeledEnumTest {

    @Test
    void shouldPinTheSalesStatuses() {
        assertEquals("Pending", SalesStatus.PENDING.getLabel());
        assertEquals("Expired", SalesStatus.EXPIRED.getLabel());
        assertEquals("Paid", SalesStatus.PAID.getLabel());
        assertEquals("Cancelled", SalesStatus.CANCELLED.getLabel());
        assertEquals("Completed", SalesStatus.COMPLETED.getLabel());
        assertEquals(5, SalesStatus.values().length);
    }

    @Test
    void shouldPrintTheLabelRatherThanTheConstantName() {
        // What ends up in a log line or a message payload.
        assertEquals("Paid", SalesStatus.PAID.toString());
        assertEquals("Cancelled", SalesStatus.CANCELLED.toString());
    }

    @Test
    void shouldExposeEveryConstantThroughTheLabeledContract() {
        for (Labeled value : SalesStatus.values()) {
            assertNotNull(value.getLabel());
            assertFalse(value.getLabel().isEmpty());
        }
    }

    @Test
    void shouldKeepTheConstantNamesApartFromTheLabels() {
        assertEquals("PENDING", SalesStatus.PENDING.name());
        assertEquals(SalesStatus.PENDING, SalesStatus.valueOf("PENDING"));
    }
}
