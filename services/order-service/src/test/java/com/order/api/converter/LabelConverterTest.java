package com.order.api.converter;

import com.order.api.enums.SalesStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the enum-to-column mapping. */
class LabelConverterTest {

    private final SalesStatusConverter status = new SalesStatusConverter();

    // ------------------------------------------------------------------
    // to the column
    // ------------------------------------------------------------------

    @Test
    void shouldWriteTheLabelNotTheConstantName() {
        assertEquals("Paid", status.convertToDatabaseColumn(SalesStatus.PAID));
    }

    @Test
    void shouldWriteNullForNull() {
        assertNull(status.convertToDatabaseColumn(null));
    }

    // ------------------------------------------------------------------
    // back from the column
    // ------------------------------------------------------------------

    @Test
    void shouldReadEverySalesStatusLabelBack() {
        for (SalesStatus value : SalesStatus.values()) {
            assertEquals(value, status.convertToEntityAttribute(value.getLabel()));
        }
    }

    @Test
    void shouldReadNullForNull() {
        assertNull(status.convertToEntityAttribute(null));
    }

    @Test
    void shouldRejectAnUnknownLabel() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> status.convertToEntityAttribute("Refunded"));

        // The message has to name both the enum and the offending value.
        assertTrue(thrown.getMessage().contains("SalesStatus"));
        assertTrue(thrown.getMessage().contains("Refunded"));
    }

    @Test
    void shouldRejectTheConstantNameItself() {
        // A column still holding 'PAID' from before the converter is bad data, and
        // must be reported rather than silently dropped.
        assertThrows(IllegalArgumentException.class, () -> status.convertToEntityAttribute("PAID"));
    }

    @Test
    void shouldBeCaseSensitive() {
        assertThrows(IllegalArgumentException.class, () -> status.convertToEntityAttribute("paid"));
    }
}
