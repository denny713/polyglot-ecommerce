package com.inventory.api.service;

import com.inventory.api.model.dto.request.po.POSearchReq;
import com.inventory.api.model.dto.request.po.POSubmitReq;
import com.inventory.api.model.dto.response.PagingResponse;
import com.inventory.api.model.dto.response.Response;

/**
 * What a purchase order can go through, from draft to approved or cancelled.
 * <p>
 * {@code doSubmit} covers both create and update — a null id means create — so a
 * draft can be revised without a separate endpoint. {@code doApprove} is the only
 * method that takes a payload, because approving reports what actually arrived.
 * <p>
 * Activate, deactivate and delete are visibility controls inherited from the
 * shared repository and are independent of the document status.
 */
public interface PurchaseOrderService {

    Response doSubmit(Long id, POSubmitReq req);

    Response doDetail(Long id);

    Response doActivate(Long id);

    Response doDeactivate(Long id);

    Response doDelete(Long id);

    Response doApprove(Long id, POSubmitReq req);

    Response doCancel(Long id);

    PagingResponse doSearch(POSearchReq req);
}
