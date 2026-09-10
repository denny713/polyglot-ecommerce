package com.inventory.api.model.dto.request.po;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * One line of a purchase order being submitted.
 * <p>
 * {@code id} carries the intent: null adds a new line, a value updates that line,
 * and a line the request omits entirely is soft-deleted by the service. Repeating
 * the same id in one request is rejected.
 * <p>
 * On approve this DTO is reused and {@code quantity} changes meaning — it is then
 * the quantity actually received, which may not exceed the ordered quantity.
 */
@Getter
@Setter
public class PODetailSubmitReq {

    private Long id;

    @NotNull(message = "Product id cannot be null")
    private Long productId;

    @NotNull(message = "Quantity cannot be null")
    @Positive(message = "Quantity must be greater than 0")
    private Integer quantity;

    private String note;
}
