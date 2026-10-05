package com.order.api.enums;

/**
 * What a notification message tells the notification service about. The names are
 * the {@code eventType} on the wire and must match the templates on the other side.
 */
public enum NotificationType {

    /** A cart line was left untouched until its TTL ran out. */
    CART_EXPIRED,

    /** A pending order's payment window ran out. */
    CHECKOUT_EXPIRED,

    /** A payment towards an order was recorded. */
    PAYMENT_SUCCEEDED,

    /** Money went back to the customer because they cancelled the order. */
    REFUND_CANCELLATION
}
