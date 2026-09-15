package com.inventory.api.model.dto.request.so;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SOSubmitReq {

    @NotBlank(message = "Document number cannot be null or empty")
    private String documentNumber;

    @Valid
    @NotEmpty(message = "Details cannot be empty")
    private List<SODetailSubmitReq> details;
}
