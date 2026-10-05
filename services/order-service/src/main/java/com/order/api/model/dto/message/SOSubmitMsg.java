package com.order.api.model.dto.message;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Payload of a sales order submit message; matches {@code SOSubmitReq} in the inventory service. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SOSubmitMsg {

    private Long id;
}
