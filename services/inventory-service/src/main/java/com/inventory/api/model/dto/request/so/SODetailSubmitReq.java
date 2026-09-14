package com.inventory.api.model.dto.request.so;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SODetailSubmitReq {

    @NotNull(message = "Product id cannot be null")
    private Long productId;

    @NotNull(message = "Quantity cannot be null")
    @Positive(message = "Quantity must be greater than 0")
    private Integer quantity;
}
