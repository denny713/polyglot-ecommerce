package com.inventory.api.model.dto.response.so;

import com.inventory.api.enums.SalesStatus;
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
public class SORes {

    private String documentNumber;
    private SalesStatus status;
    private BigDecimal grandTotal;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private List<SODetailRes> details;
}
