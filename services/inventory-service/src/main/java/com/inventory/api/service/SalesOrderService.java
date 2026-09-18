package com.inventory.api.service;

import com.inventory.api.model.dto.request.so.SOSubmitReq;
import com.inventory.api.model.dto.response.Response;

/** The one thing a sales order asks of inventory: move the stock it sold. */
public interface SalesOrderService {

    Response doSubmit(SOSubmitReq req);
}
