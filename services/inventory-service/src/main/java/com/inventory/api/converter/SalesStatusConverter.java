package com.inventory.api.converter;

import com.inventory.api.enums.SalesStatus;
import jakarta.persistence.Converter;

/** Persists {@link com.inventory.api.enums.SalesStatus} as its label. */
@Converter(autoApply = true)
public class SalesStatusConverter extends LabelConverter<SalesStatus>{

    public SalesStatusConverter() {
        super(SalesStatus.class);
    }
}
