package com.inventory.api.converter;

import com.inventory.api.enums.StockActivity;
import jakarta.persistence.Converter;

/**
 * Persists {@link com.inventory.api.enums.StockActivity} as its label.
 * <p>
 * {@code autoApply = true} means every entity field of that type is converted
 * without being annotated, so the enum can gain a new constant without touching
 * the entities.
 */
@Converter(autoApply = true)
public class StockActivityConverter extends LabelConverter<StockActivity> {

    public StockActivityConverter() {
        super(StockActivity.class);
    }
}
