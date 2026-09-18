package com.order.api.exception;

/** The caller is known but not allowed to do this. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
