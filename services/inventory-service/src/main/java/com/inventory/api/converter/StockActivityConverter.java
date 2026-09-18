package com.inventory.api.converter;

import com.inventory.api.enums.StockActivity;
import jakarta.persistence.Converter;

/** Persists {@link com.inventory.api.enums.StockActivity} as its label. */
@Converter(autoApply = true)
public class StockActivityConverter extends LabelConverter<StockActivity> {

    public StockActivityConverter() {
        super(StockActivity.class);
    }
}
