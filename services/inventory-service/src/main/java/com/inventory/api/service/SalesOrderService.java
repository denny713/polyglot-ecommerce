package com.inventory.api.service;

import com.inventory.api.model.dto.request.so.SOSubmitReq;
import com.inventory.api.model.dto.response.Response;

/**
 * The one thing a sales order asks of inventory: move the stock it sold.
 * <p>
 * A single method, because this service does not own the document's lifecycle.
 * There is no draft, approve or cancel to mirror here — the order service owns
 * those, and reaches inventory only once, when payment settles.
 * <p>
 * {@code doSubmit} also takes no separate id argument, unlike the purchase
 * services: the id travels inside the message payload rather than in a path.
 */
public interface SalesOrderService {

    Response doSubmit(SOSubmitReq req);
}
