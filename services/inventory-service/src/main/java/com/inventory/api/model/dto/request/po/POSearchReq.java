package com.inventory.api.model.dto.request.po;

import com.inventory.api.enums.DocStatus;
import com.inventory.api.model.dto.request.PageReq;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Filters for {@code POST /po/list}, on top of the paging fields of {@code PageReq}. */
@Getter
@Setter
public class POSearchReq extends PageReq {

    private String documentNumber;
    private Long supplierId;
    private DocStatus status;
    private BigDecimal minOrderGrandTotal;
    private BigDecimal maxOrderGrandTotal;
    private BigDecimal minRealGrandTotal;
    private BigDecimal maxRealGrandTotal;
    private Boolean isActive;
    private LocalDateTime createdFrom;
    private LocalDateTime createdTo;
}
