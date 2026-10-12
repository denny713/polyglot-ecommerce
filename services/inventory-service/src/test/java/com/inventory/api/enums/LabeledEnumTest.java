package com.inventory.api.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pins the labels of the three labelled enums. */
class LabeledEnumTest {

    @Test
    void shouldPinTheDocumentStatuses() {
        assertEquals("Draft", DocStatus.DRAFT.getLabel());
        assertEquals("Approved", DocStatus.APPROVED.getLabel());
        assertEquals("Cancelled", DocStatus.CANCELLED.getLabel());
        assertEquals(3, DocStatus.values().length);
    }

    @Test
    void shouldPinTheDocumentTypes() {
        assertEquals("Sales Order", DocType.SO.getLabel());
        assertEquals("Sales Refund", DocType.SR.getLabel());
        assertEquals("Purchase Order", DocType.PO.getLabel());
        assertEquals("Purchase Return", DocType.PR.getLabel());
        assertEquals(4, DocType.values().length);
    }

    @Test
    void shouldPinTheRefundReasons() {
        assertEquals("Overpayment", RefundReason.OVERPAYMENT.getLabel());
        assertEquals("Cancellation", RefundReason.CANCELLATION.getLabel());
        assertEquals("Expired", RefundReason.EXPIRED.getLabel());
        assertEquals(3, RefundReason.values().length);
    }

    @Test
    void shouldPinTheSalesStatus() {
        assertEquals("Pending", SalesStatus.PENDING.getLabel());
        assertEquals("Expired", SalesStatus.EXPIRED.getLabel());
        assertEquals("Paid", SalesStatus.PAID.getLabel());
        assertEquals("Cancelled", SalesStatus.CANCELLED.getLabel());
        assertEquals("Completed", SalesStatus.COMPLETED.getLabel());
        assertEquals(5, SalesStatus.values().length);
    }

    @Test
    void shouldPinTheStockActivities() {
        assertEquals("Stock In", StockActivity.SI.getLabel());
        assertEquals("Stock Out", StockActivity.SO.getLabel());
        assertEquals(2, StockActivity.values().length);
    }

    @Test
    void shouldPrintTheLabelRatherThanTheConstantName() {
        assertEquals("Approved", DocStatus.APPROVED.toString());
        assertEquals("Purchase Return", DocType.PR.toString());
        assertEquals("Stock Out", StockActivity.SO.toString());
        assertEquals("Paid", SalesStatus.PAID.toString());
    }

    @Test
    void shouldKeepTheConstantNamesTheDocumentPrefixesDependOn() {
        assertEquals("SO", DocType.SO.name());
        assertEquals("PO", DocType.PO.name());
        assertEquals("PR", DocType.PR.name());
    }

    @Test
    void shouldExposeEveryConstantThroughTheLabeledContract() {
        for (Labeled value : DocStatus.values()) {
            assertNotNull(value.getLabel());
            assertFalse(value.getLabel().isEmpty());
        }
    }
}
