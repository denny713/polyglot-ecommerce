package com.inventory.api.model.dto.request.pr;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * One line of a purchase return being submitted.
 * <p>
 * {@code id} carries the intent: null adds a new line, a value updates that line,
 * and a line the request omits entirely is soft-deleted by the service. Repeating
 * the same id in one request is rejected.
 * <p>
 * {@code reason} is per line, and is separate from the document-level reason on
 * {@code PRSubmitReq} — one return can send items back for different causes.
 */
@Getter
@Setter
public class PRDetailSubmitReq {

    private Long id;

    @NotNull(message = "Product id cannot be null")
    private Long productId;

    @NotNull(message = "Quantity cannot be null")
    @Positive(message = "Quantity must be greater than 0")
    private Integer quantity;

    private String reason;
    private String note;
}
