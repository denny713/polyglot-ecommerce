package com.inventory.api.exception;

/**
 * The caller is known but not allowed to do this.
 * <p>
 * {@code ResponseHandler} answers 403. Nothing throws it today: authorization is
 * settled in {@code TokenFilter}, which runs before Spring MVC and writes its own
 * 403 body. It exists for the case where a rule depends on data only the service
 * layer has loaded.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
