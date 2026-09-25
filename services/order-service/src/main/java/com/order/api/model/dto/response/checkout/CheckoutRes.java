package com.order.api.model.dto.response.checkout;

import com.order.api.enums.SalesStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CheckoutRes {

    private Long id;
    private String documentNumber;
    private BigDecimal grandTotal;
    private SalesStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime expiredAt;
    private List<CheckoutDetailRes> items;
}
