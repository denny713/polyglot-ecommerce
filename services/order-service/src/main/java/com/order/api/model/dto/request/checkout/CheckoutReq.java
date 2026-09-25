package com.order.api.model.dto.request.checkout;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * The products to turn into a sales order. From the cart, each line must name the
 * quantity the cart holds, so the customer is billed for what they saw; buying
 * straight away takes one product only. The user is taken from the access token.
 */
@Getter
@Setter
public class CheckoutReq {

    @NotNull(message = "fromCart cannot be null")
    private Boolean fromCart;

    @Valid
    @NotEmpty(message = "Items cannot be empty")
    private List<CheckoutDetailReq> items;

    @JsonIgnore
    @AssertTrue(message = "Buy now can only hold one product")
    public boolean isSingleItemWhenBuyNow() {
        return !Boolean.FALSE.equals(fromCart) || items == null || items.size() == 1;
    }
}
