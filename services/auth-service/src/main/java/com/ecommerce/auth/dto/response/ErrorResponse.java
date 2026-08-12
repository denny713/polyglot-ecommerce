package com.ecommerce.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error body shape used by every endpoint in this service.
 *
 * @param status    HTTP status code
 * @param error     stable, machine-readable error code, e.g. {@code INVALID_CREDENTIALS}
 * @param message   short human-readable explanation
 * @param details   per-field error list; only populated for validation failures
 * @param timestamp server time at which the error was created
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String error,
        String message,
        List<FieldError> details,
        Instant timestamp) {

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, null, Instant.now());
    }

    public static ErrorResponse of(int status, String error, String message, List<FieldError> details) {
        return new ErrorResponse(status, error, message, details, Instant.now());
    }

    /**
     * A single validation violation on a single field.
     */
    public record FieldError(String field, String message) {
    }
}
