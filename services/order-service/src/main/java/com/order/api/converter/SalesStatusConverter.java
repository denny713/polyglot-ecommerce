package com.order.api.converter;

import com.order.api.enums.SalesStatus;
import jakarta.persistence.Converter;

/** Persists {@link com.order.api.enums.SalesStatus} as its label. */
@Converter(autoApply = true)
public class SalesStatusConverter extends LabelConverter<SalesStatus> {

    public SalesStatusConverter() {
        super(SalesStatus.class);
    }
}
