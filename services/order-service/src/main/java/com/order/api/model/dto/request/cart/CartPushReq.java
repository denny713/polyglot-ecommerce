package com.order.api.model.dto.request.cart;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * A product to put in the cart, and how many of it the line should hold. The user is
 * taken from the access token, never from the payload.
 */
@Getter
@Setter
public class CartPushReq {

    @NotNull(message = "Product id cannot be null")
    private Long productId;

    @NotNull(message = "Quantity cannot be null")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;
}
