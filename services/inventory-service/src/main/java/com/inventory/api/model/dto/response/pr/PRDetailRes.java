package com.inventory.api.model.dto.response.pr;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PRDetailRes {

    private Long id;
    private Long productId;
    private String productName;
    private Integer orderQuantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private String reason;
    private String note;
}
