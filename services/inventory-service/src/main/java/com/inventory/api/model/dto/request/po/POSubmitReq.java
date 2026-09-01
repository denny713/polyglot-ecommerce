package com.inventory.api.model.dto.request.po;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

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
