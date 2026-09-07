package com.inventory.api.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the labels of the three labelled enums.
 * <p>
 * These are not cosmetic: {@code LabelConverter} writes them into the database,
 * so changing one is a data migration rather than a rename. A test that fails on
 * an edited label is the point.
 */
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
        assertEquals("Purchase Order", DocType.PO.getLabel());
        assertEquals("Purchase Return", DocType.PR.getLabel());
        assertEquals(2, DocType.values().length);
    }

    @Test
    void shouldPinTheStockActivities() {
        assertEquals("Stock In", StockActivity.SI.getLabel());
        assertEquals("Stock Out", StockActivity.SO.getLabel());
        assertEquals(2, StockActivity.values().length);
    }

    @Test
    void shouldPrintTheLabelRatherThanTheConstantName() {
        // toString is what shows up in log lines and error messages.
        assertEquals("Approved", DocStatus.APPROVED.toString());
        assertEquals("Purchase Return", DocType.PR.toString());
        assertEquals("Stock Out", StockActivity.SO.toString());
    }

    @Test
    void shouldKeepTheConstantNamesTheDocumentPrefixesDependOn() {
        // DocType.name() is passed to generate_doc_no, so renaming a constant
        // silently changes every document number that function produces.
        assertEquals("PO", DocType.PO.name());
        assertEquals("PR", DocType.PR.name());
    }

    @Test
    void shouldExposeEveryConstantThroughTheLabeledContract() {
        for (Labeled value : DocStatus.values()) {
            assertNotNull(value.getLabel());
            assertTrue(value.getLabel().length() > 0);
        }
    }
}
