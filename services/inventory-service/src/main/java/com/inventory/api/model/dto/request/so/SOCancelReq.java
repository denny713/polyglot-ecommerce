package com.inventory.api.model.dto.request.so;

import lombok.Getter;
import lombok.Setter;

/** Payload of a sales order cancel message. */
@Getter
@Setter
public class SOCancelReq {

    private Long id;
}
