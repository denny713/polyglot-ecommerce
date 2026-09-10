package com.inventory.api.model.dto.request.pr;

import com.inventory.api.enums.DocStatus;
import com.inventory.api.model.dto.request.PageReq;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Filters for {@code POST /pr/list}, on top of the paging fields of
 * {@code PageReq}.
 * <p>
 * Every field is optional and an omitted one is simply not applied. There is one
 * grand-total range here rather than two, because a return has no
 * ordered-versus-received split.
 */
@Getter
@Setter
public class PRSearchReq extends PageReq {

    private String documentNumber;
    private Long supplierId;
    private DocStatus status;
    private BigDecimal minGrandTotal;
    private BigDecimal maxGrandTotal;
    private Boolean isActive;
    private LocalDateTime createdFrom;
    private LocalDateTime createdTo;
}
