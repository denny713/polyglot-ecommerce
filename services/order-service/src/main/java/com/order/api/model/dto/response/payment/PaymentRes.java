package com.order.api.model.dto.response.payment;

import com.order.api.enums.PaymentMethod;
import com.order.api.enums.SalesStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PaymentRes {

    private Long id;
    private String salesOrderDocNo;
    private String paymentDocNo;
    private String reference;
    private PaymentMethod method;
    private String bankName;
    private String accountNumber;
    private String accountName;
    private BigDecimal amount;
    private BigDecimal appliedAmount;
    private BigDecimal excessAmount;
    private LocalDateTime paidAt;

    /** Where the order stands after this payment. */
    private SalesStatus salesOrderStatus;
    private BigDecimal paid;
    private BigDecimal outstanding;
}
