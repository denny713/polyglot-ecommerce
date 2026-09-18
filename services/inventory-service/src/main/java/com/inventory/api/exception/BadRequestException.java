package com.inventory.api.exception;

/**
 * The caller sent something this service refuses to act on — a state transition
 * that is not allowed, or a payload that is internally inconsistent.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
