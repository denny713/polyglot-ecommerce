package com.inventory.api.model.dto.response.pr;

import com.inventory.api.enums.DocStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PRRes {

    private Long id;
    private String documentNumber;
    private Long supplierId;
    private String supplierName;
    private DocStatus status;
    private BigDecimal grandTotal;
    private String reason;
    private String note;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private List<PRDetailRes> details;
}
