package com.order.api.exception;

/** Something failed that the caller cannot fix. */
public class ServiceException extends RuntimeException {

    public ServiceException(String message) {
        super(message);
    }
}
