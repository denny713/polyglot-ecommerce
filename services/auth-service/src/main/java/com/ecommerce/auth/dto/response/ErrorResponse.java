package com.ecommerce.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

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
@Schema(
        name = "ErrorResponse",
        description = """
                The body returned by every failing call, whatever the status code. \
                Branch on `error`, not on `message`: the code is part of the contract, \
                the message may be reworded.
                """)
public record ErrorResponse(

        @Schema(description = "HTTP status code, repeated in the body for clients that only log it", examples = "401")
        int status,

        @Schema(
                description = "Stable, machine-readable error code",
                examples = "INVALID_CREDENTIALS",
                enumeration = {
                        "VALIDATION_ERROR",
                        "INVALID_CREDENTIALS",
                        "INVALID_REFRESH_TOKEN",
                        "ACCOUNT_LOCKED",
                        "ACCOUNT_DISABLED",
                        "ACCOUNT_ALREADY_EXISTS",
                        "ACCOUNT_NOT_FOUND",
                        "ACCOUNT_ACCESS_DENIED",
                        "INVALID_ACCOUNT_DATA",
                        "IDENTITY_PROVIDER_UNAVAILABLE",
                        "NOTIFICATION_UNAVAILABLE"
                })
        String error,

        @Schema(description = "Short human-readable explanation", examples = "Invalid username or password")
        String message,

        @Schema(description = "Per-field errors; present only when `error` is `VALIDATION_ERROR`")
        List<FieldError> details,

        @Schema(description = "Server time at which the error was created", examples = "2026-08-19T09:15:30.123Z")
        Instant timestamp) {

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, null, Instant.now());
    }

    public static ErrorResponse of(int status, String error, String message, List<FieldError> details) {
        return new ErrorResponse(status, error, message, details, Instant.now());
    }

    /** A single validation violation on a single field. */
    @Schema(name = "FieldError", description = "A single validation violation on a single field")
    public record FieldError(

            @Schema(description = "Name of the offending request field", examples = "username")
            String field,

            @Schema(description = "What is wrong with it", examples = "username is required")
            String message) {
    }
}
