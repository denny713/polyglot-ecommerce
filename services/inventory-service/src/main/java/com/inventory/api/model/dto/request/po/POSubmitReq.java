package com.inventory.api.model.dto.request.po;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Payload for creating or updating a purchase order, and for approving one.
 * <p>
 * The details list must be non-empty, since a purchase order with no lines has
 * nothing to order. When used for approve, the supplier is ignored and each
 * detail's {@code id} must already belong to the document.
 */
@Getter
@Setter
public class POSubmitReq {

    @NotNull(message = "Supplier id cannot be null")
    private Long supplierId;
    private String note;

    @Valid
    @NotEmpty(message = "Details cannot be empty")
    private List<PODetailSubmitReq> details;
}
