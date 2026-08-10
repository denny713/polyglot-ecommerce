package com.ecommerce.auth.exception.handler;

import com.ecommerce.auth.dto.response.ErrorResponse;
import com.ecommerce.auth.exception.AuthErrorCode;
import com.ecommerce.auth.exception.AuthenticationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Menerjemahkan seluruh turunan {@link AuthenticationException} menjadi response
 * HTTP — padanan {@code @RestControllerAdvice} di Spring Boot.
 *
 * <p>Cukup satu mapper untuk semua turunannya: yang dibaca hanya
 * {@link AuthenticationException#errorCode()}, bukan tipe konkretnya. Menambah
 * jenis kegagalan baru berarti menambah satu {@code case} di sini, bukan
 * menyentuh controller (Open/Closed Principle).
 */
@Provider
public class AuthenticationExceptionMapper implements ExceptionMapper<AuthenticationException> {

    @Override
    public Response toResponse(AuthenticationException exception) {
        Response.Status status = statusOf(exception.errorCode());

        return Response.status(status)
                .entity(ErrorResponse.of(
                        status.getStatusCode(),
                        exception.errorCode().name(),
                        exception.getMessage()))
                .build();
    }

    private Response.Status statusOf(AuthErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_CREDENTIALS -> Response.Status.UNAUTHORIZED;   // 401
            case ACCOUNT_LOCKED -> Response.Status.TOO_MANY_REQUESTS;   // 429
            case ACCOUNT_DISABLED -> Response.Status.FORBIDDEN;         // 403
        };
    }
}
