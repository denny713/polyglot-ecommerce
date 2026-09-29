package com.inventory.api.model.dto.request.so;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Payload of a sales order cancel message. */
@Getter
@Setter
public class SOCancelReq {

    private Long id;

    /** The refunds that took the order back; the returned stock is recorded under each of them. */
    private List<Long> refundIds;
}
