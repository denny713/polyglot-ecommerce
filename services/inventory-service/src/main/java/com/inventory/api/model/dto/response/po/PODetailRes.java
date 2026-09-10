package com.inventory.api.model.dto.response.po;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One purchase order line as returned to the caller.
 * <p>
 * The product is flattened to an id and a name so the client needs no second call,
 * and both subtotals are exposed: {@code orderSubtotal} is what was ordered,
 * {@code realSubtotal} stays zero until the document is approved.
 */
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
