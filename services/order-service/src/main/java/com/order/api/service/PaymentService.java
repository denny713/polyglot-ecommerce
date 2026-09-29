package com.order.api.service;

import com.order.api.model.dto.request.payment.PaymentReq;
import com.order.api.model.dto.response.Response;

public interface PaymentService {

    Response doPayment(PaymentReq req);

    Response doCancel(Long salesOrderId);
}
