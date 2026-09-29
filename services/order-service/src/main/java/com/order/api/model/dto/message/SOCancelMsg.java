package com.order.api.model.dto.message;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Payload of a sales order cancel message; matches {@code SOCancelReq} in the inventory service. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SOCancelMsg {

    private Long id;
}
