package com.inventory.api.exception;

/**
 * Something failed that the caller cannot fix.
 * <p>
 * {@code ResponseHandler} answers 500 and — unlike the other handlers — replaces
 * the message with a generic one, logging the real text instead, so internals are
 * not exposed to the caller.
 */
public class ServiceException extends RuntimeException {

    public ServiceException(String message) {
        super(message);
    }
}
