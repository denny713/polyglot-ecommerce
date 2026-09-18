package com.inventory.api.model.dto.response.po;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** One purchase order line as returned to the caller. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PODetailRes {

    private Long id;
    private Long productId;
    private String productName;
    private Integer orderQuantity;
    private BigDecimal unitPrice;
    private BigDecimal orderSubtotal;
    private BigDecimal realSubtotal;
    private String note;
}
