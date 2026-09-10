package com.inventory.api.converter;

import com.inventory.api.enums.Labeled;
import jakarta.persistence.AttributeConverter;

import java.util.Arrays;

/**
 * Stores an enum by its {@code label} instead of its {@code name()}.
 * <p>
 * The point is the database side: {@code status} reads {@code 'Approved'} rather
 * than {@code 'APPROVED'}, and {@code document_type} reads {@code 'Purchase
 * Order'} rather than {@code 'PO'}, so the rows are legible without consulting the
 * Java source. Renaming a constant therefore stays safe, but changing a label is a
 * data migration.
 * <p>
 * An unrecognised label fails loudly rather than mapping to null, so a bad value
 * surfaces on read instead of silently becoming missing data.
 */
public abstract class LabelConverter<E extends Enum<E> & Labeled> implements AttributeConverter<E, String> {

    private final Class<E> type;

    protected LabelConverter(Class<E> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(E value) {
        return (value == null) ? null : value.getLabel();
    }

    @Override
    public E convertToEntityAttribute(String label) {
        if (label == null) {
            return null;
        }

        return Arrays.stream(type.getEnumConstants())
                .filter(value -> value.getLabel().equals(label))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "Unknown %s label %s", type.getSimpleName(), label)));
    }
}
