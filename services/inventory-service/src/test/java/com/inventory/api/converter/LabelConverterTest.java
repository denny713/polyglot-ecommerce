package com.inventory.api.converter;

import com.inventory.api.enums.DocStatus;
import com.inventory.api.enums.DocType;
import com.inventory.api.enums.StockActivity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the enum-to-column mapping. */
class LabelConverterTest {

    private final DocStatusConverter status = new DocStatusConverter();
    private final DocTypeConverter type = new DocTypeConverter();
    private final StockActivityConverter activity = new StockActivityConverter();

    // ------------------------------------------------------------------
    // to the column
    // ------------------------------------------------------------------

    @Test
    void shouldWriteTheLabelNotTheConstantName() {
        assertEquals("Approved", status.convertToDatabaseColumn(DocStatus.APPROVED));
        assertEquals("Purchase Order", type.convertToDatabaseColumn(DocType.PO));
        assertEquals("Stock In", activity.convertToDatabaseColumn(StockActivity.SI));
    }

    @Test
    void shouldWriteNullForNull() {
        assertNull(status.convertToDatabaseColumn(null));
    }

    // ------------------------------------------------------------------
    // back from the column
    // ------------------------------------------------------------------

    @Test
    void shouldReadEveryDocStatusLabelBack() {
        for (DocStatus value : DocStatus.values()) {
            assertEquals(value, status.convertToEntityAttribute(value.getLabel()));
        }
    }

    @Test
    void shouldReadEveryDocTypeLabelBack() {
        for (DocType value : DocType.values()) {
            assertEquals(value, type.convertToEntityAttribute(value.getLabel()));
        }
    }

    @Test
    void shouldReadEveryStockActivityLabelBack() {
        for (StockActivity value : StockActivity.values()) {
            assertEquals(value, activity.convertToEntityAttribute(value.getLabel()));
        }
    }

    @Test
    void shouldReadNullForNull() {
        assertNull(status.convertToEntityAttribute(null));
    }

    @Test
    void shouldRejectAnUnknownLabel() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> status.convertToEntityAttribute("Rejected"));

        // The message has to name both the enum and the offending value.
        assertTrue(thrown.getMessage().contains("DocStatus"));
        assertTrue(thrown.getMessage().contains("Rejected"));
    }

    @Test
    void shouldRejectTheConstantNameItself() {
        // A column still holding 'APPROVED' from before the converter is bad data,
        // and must be reported rather than silently dropped.
        assertThrows(IllegalArgumentException.class, () -> status.convertToEntityAttribute("APPROVED"));
    }

    @Test
    void shouldBeCaseSensitive() {
        assertThrows(IllegalArgumentException.class, () -> status.convertToEntityAttribute("approved"));
    }
}
