package com.order.api.model.dto.request.payment;

import com.order.api.enums.PaymentMethod;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PaymentReq {

    @NotNull(message = "Sales order id cannot be null")
    private Long salesOrderId;

    /** The payment's id at the bank or gateway. Sending it again returns the payment already recorded. */
    @NotBlank(message = "Reference cannot be null or empty")
    @Size(max = 100, message = "Reference cannot be longer than 100 characters")
    private String reference;

    @NotNull(message = "Payment method cannot be null")
    private PaymentMethod method;

    @NotBlank(message = "Bank name cannot be null or empty")
    private String bankName;

    @NotBlank(message = "Account number cannot be null or empty")
    private String accountNumber;

    @NotBlank(message = "Account name cannot be null or empty")
    private String accountName;

    @NotNull(message = "Amount cannot be null")
    @Positive(message = "Amount must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "Amount must have at most 10 digits and 2 decimals")
    private BigDecimal amount;
}
