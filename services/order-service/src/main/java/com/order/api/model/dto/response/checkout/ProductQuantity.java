package com.order.api.model.dto.response.checkout;

/** A quantity per product, as the stock queries of checkout return it. */
public interface ProductQuantity {

    Long getProductId();

    Integer getQuantity();
}
