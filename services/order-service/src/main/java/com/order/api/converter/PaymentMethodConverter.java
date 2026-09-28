package com.order.api.converter;

import com.order.api.enums.PaymentMethod;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PaymentMethodConverter extends LabelConverter<PaymentMethod> {

    public PaymentMethodConverter() {
        super(PaymentMethod.class);
    }
}
