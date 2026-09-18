package com.ecommerce.auth.handler;

import com.ecommerce.auth.dto.response.ErrorResponse;
import com.ecommerce.auth.enums.AuthErrorCode;
import com.ecommerce.auth.exception.AuthenticationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Translates every subclass of {@link AuthenticationException} into an HTTP
 * response — the equivalent of {@code @RestControllerAdvice} in Spring Boot.
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
            case INVALID_CREDENTIALS, INVALID_REFRESH_TOKEN -> Response.Status.UNAUTHORIZED;   // 401
            case ACCOUNT_LOCKED -> Response.Status.TOO_MANY_REQUESTS;   // 429
            case ACCOUNT_DISABLED -> Response.Status.FORBIDDEN;         // 403
        };
    }
}
