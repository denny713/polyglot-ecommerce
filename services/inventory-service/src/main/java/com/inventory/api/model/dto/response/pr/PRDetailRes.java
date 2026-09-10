package com.inventory.api.model.dto.response.pr;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One purchase return line as returned to the caller.
 * <p>
 * The product is flattened to an id and a name. There is a single
 * {@code subtotal}: a return has no ordered-versus-received distinction.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PRDetailRes {

    private Long id;
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private String note;
}
