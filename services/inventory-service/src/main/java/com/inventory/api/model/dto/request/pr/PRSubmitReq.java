package com.inventory.api.model.dto.request.pr;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Payload for creating or updating a purchase return.
 * <p>
 * The details list must be non-empty. {@code reason} explains the return as a
 * whole; a per-line cause belongs on {@code PRDetailSubmitReq}. Both
 * {@code reason} and {@code note} fall back to {@code "-"} when blank, so the
 * stored document never carries nulls in those columns.
 */
@Getter
@Setter
public class PRSubmitReq {

    @NotNull(message = "Supplier id cannot be null")
    private Long supplierId;
    private String reason;
    private String note;

    @Valid
    @NotEmpty(message = "Details cannot be empty")
    private List<PRDetailSubmitReq> details;
}
