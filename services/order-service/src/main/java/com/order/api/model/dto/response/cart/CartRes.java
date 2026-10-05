package com.order.api.model.dto.response.cart;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** A cart line as it now stands in the cache. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CartRes {

    private UUID userId;
    private Long productId;
    private Integer quantity;
}
