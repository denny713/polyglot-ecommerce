package com.order.api.model.dto.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Payload of a notification message. It carries ids only: the notification service
 * reads the order, payment, refund or product from the database itself, so nothing
 * here can go stale or leak more than the id of a row.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationMsg {

    private String eventId;
    private String eventType;
    private String occurredAt;
    private String source;

    /** The sales order, payment or refund the event is about. */
    private Long id;

    /** The customer whose cart line expired; only set for a cart event. */
    private UUID userId;

    /** The product of the cart line that expired; only set for a cart event. */
    private Long productId;
}
