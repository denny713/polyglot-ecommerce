package com.inventory.api.service;

import com.inventory.api.model.dto.request.so.SOSubmitReq;
import com.inventory.api.model.dto.response.Response;

public interface SalesOrderService {

    Response doSubmit(SOSubmitReq req);
}
