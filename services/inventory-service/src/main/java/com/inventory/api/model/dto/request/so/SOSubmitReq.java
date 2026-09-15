package com.inventory.api.model.dto.request.so;

import lombok.Getter;
import lombok.Setter;

/**
 * Payload of a sales order submit message.
 * <p>
 * It carries the document id and nothing else. The lines, prices and totals are
 * already in this service's own tables, so repeating them in the message would only
 * create a second version of the truth that could disagree with the stored one.
 */
@Getter
@Setter
public class SOSubmitReq {

    private Long id;
}
