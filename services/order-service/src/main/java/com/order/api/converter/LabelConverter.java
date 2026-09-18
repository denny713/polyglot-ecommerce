package com.order.api.converter;

import com.order.api.enums.Labeled;
import jakarta.persistence.AttributeConverter;

import java.util.Arrays;

/** Stores an enum by its {@code label} instead of its {@code name()}. */
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
