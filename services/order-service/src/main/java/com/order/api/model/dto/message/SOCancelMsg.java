package com.order.api.model.dto.message;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Payload of a sales order cancel message; matches {@code SOCancelReq} in the inventory service. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SOCancelMsg {

    private Long id;

    /**
     * Every refund that took the order back, one per instalment; the inventory service
     * reads them and records the returned stock under each in proportion to its amount.
     */
    private List<Long> refundIds;
}
