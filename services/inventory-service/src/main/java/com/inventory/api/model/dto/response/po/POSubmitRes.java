package com.inventory.api.model.dto.response.po;

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
public class POSubmitRes {

    private Long id;
    private String documentNumber;
    private Long supplierId;
    private String supplierName;
    private DocStatus status;
    private BigDecimal grandTotal;
    private String note;
    private LocalDateTime createdAt;
    private List<PODetailSubmitRes> details;
}
