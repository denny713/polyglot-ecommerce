package com.order.api.service;

import com.order.api.model.dto.request.checkout.CheckoutReq;
import com.order.api.model.dto.response.Response;

/** Turns what a customer picked into a pending sales order. */
public interface CheckoutService {

    Response doCheckout(CheckoutReq req);
}
