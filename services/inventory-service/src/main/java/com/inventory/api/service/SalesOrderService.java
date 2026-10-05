package com.inventory.api.service;

import com.inventory.api.model.dto.request.so.SOCancelReq;
import com.inventory.api.model.dto.request.so.SOSubmitReq;
import com.inventory.api.model.dto.response.Response;

/** What a sales order asks of inventory: move the stock it sold, and put it back when cancelled. */
public interface SalesOrderService {

    Response doSubmit(SOSubmitReq req);

    Response doCancel(SOCancelReq req);
}
