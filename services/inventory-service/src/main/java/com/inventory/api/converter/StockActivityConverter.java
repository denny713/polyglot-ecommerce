package com.inventory.api.converter;

import com.inventory.api.enums.StockActivity;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class StockActivityConverter extends LabelConverter<StockActivity> {

    public StockActivityConverter() {
        super(StockActivity.class);
    }
}
