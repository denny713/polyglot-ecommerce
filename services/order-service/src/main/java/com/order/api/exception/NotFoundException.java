package com.order.api.exception;

/** The row the caller referred to does not exist, or is soft-deleted and therefore invisible. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
