package com.inventory.api.service;

import com.inventory.api.model.dto.request.pr.PRSearchReq;
import com.inventory.api.model.dto.request.pr.PRSubmitReq;
import com.inventory.api.model.dto.response.PagingResponse;
import com.inventory.api.model.dto.response.Response;

public interface PurchaseReturnService {

    Response doSubmit(Long id, PRSubmitReq req);

    Response doDetail(Long id);

    Response doActivate(Long id);

    Response doDeactivate(Long id);

    Response doDelete(Long id);

    Response doApprove(Long id);

    Response doCancel(Long id);

    PagingResponse doSearch(PRSearchReq req);
}
