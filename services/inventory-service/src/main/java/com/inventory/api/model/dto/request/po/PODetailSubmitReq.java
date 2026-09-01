package com.inventory.api.model.dto.request.po;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PODetailSubmitReq {

    private Long id;

    @NotNull(message = "Product id cannot be null")
    private Long productId;

    @NotNull(message = "Order quantity cannot be null")
    @Positive(message = "Order quantity must be greater than 0")
    private Integer orderQuantity;

    private String note;
}
