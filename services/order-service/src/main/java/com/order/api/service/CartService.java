package com.order.api.service;

import com.order.api.model.dto.request.cart.CartPushReq;
import com.order.api.model.dto.response.Response;

/** What a customer can do with their cart while it still lives in the cache. */
public interface CartService {

    Response doPush(CartPushReq req);

    Response doRemove(Long productId);
}
