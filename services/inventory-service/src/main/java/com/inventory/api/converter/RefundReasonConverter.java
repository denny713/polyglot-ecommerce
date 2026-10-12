package com.inventory.api.converter;

import com.inventory.api.enums.RefundReason;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RefundReasonConverter extends LabelConverter<RefundReason> {

    public RefundReasonConverter() {
        super(RefundReason.class);
    }
}
