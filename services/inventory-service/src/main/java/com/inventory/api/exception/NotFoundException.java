package com.inventory.api.exception;

/**
 * The row the caller referred to does not exist, or is soft-deleted and therefore
 * invisible.
 * <p>
 * {@code ResponseHandler} answers 404. Most of these come from
 * {@code CommonRepositoryImpl}, which already names the entity and id in the
 * message.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
