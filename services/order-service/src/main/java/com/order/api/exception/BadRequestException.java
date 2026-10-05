package com.order.api.exception;

/**
 * The caller sent something this service refuses to act on — checking out an empty
 * cart, a quantity that is not positive, or a payload that is internally
 * inconsistent.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
