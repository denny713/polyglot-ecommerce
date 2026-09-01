package com.inventory.api.service;

import com.inventory.api.model.dto.request.po.POSearchReq;
import com.inventory.api.model.dto.request.po.POSubmitReq;
import com.inventory.api.model.dto.response.PagingResponse;
import com.inventory.api.model.dto.response.Response;

public interface PurchaseOrderService {

    Response doSubmit(Long id, POSubmitReq req);

    Response doDetail(Long id);

    Response doActivate(Long id);

    Response doDeactivate(Long id);

    Response doDelete(Long id);

    PagingResponse doSearch(POSearchReq req);
}
