package com.order.api.service;

import com.order.api.enums.RefundReason;
import com.order.api.model.entity.Payment;
import com.order.api.model.entity.Refund;
import com.order.api.model.entity.SalesOrder;

import java.util.List;

public interface RefundService {

    Refund doRefundExcess(Payment payment);

    List<Refund> doRefundOrder(SalesOrder order, RefundReason reason);
}
