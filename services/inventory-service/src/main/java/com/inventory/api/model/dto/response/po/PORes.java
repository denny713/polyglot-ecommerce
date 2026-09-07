package com.inventory.api.model.dto.response.po;

import com.inventory.api.enums.DocStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A purchase order as returned to the caller.
 * <p>
 * Assembled with {@code BeanUtils.copyProperties} plus the supplier fields copied
 * by hand, so a field only appears here if it is named exactly as on the entity —
 * renaming one silently drops it from the response.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PORes {

    private Long id;
    private String documentNumber;
    private Long supplierId;
    private String supplierName;
    private DocStatus status;
    private BigDecimal orderGrandTotal;
    private BigDecimal realGrandTotal;
    private String note;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private List<PODetailRes> details;
}
