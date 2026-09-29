package com.order.api.model.dto.response.payment;

import com.order.api.enums.SalesStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/** A paid order taken back, and what was refunded for it. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PaymentCancelRes {

    private Long salesOrderId;
    private String salesOrderDocNo;
    private SalesStatus status;
    private BigDecimal grandTotal;
    private BigDecimal refundAmount;
    private List<String> refundDocNos;
}
