package com.ecommerce.auth.handler;

import com.ecommerce.auth.dto.response.ErrorResponse;
import com.ecommerce.auth.enums.AccountErrorCode;
import com.ecommerce.auth.exception.AccountException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Translates every subclass of {@link AccountException} into an HTTP response,
 * exactly as {@link AuthenticationExceptionMapper} does for authentication
 * failures.
 */
@Provider
public class AccountExceptionMapper implements ExceptionMapper<AccountException> {

    @Override
    public Response toResponse(AccountException exception) {
        Response.Status status = statusOf(exception.errorCode());

        return Response.status(status)
                .entity(ErrorResponse.of(
                        status.getStatusCode(),
                        exception.errorCode().name(),
                        exception.getMessage()))
                .build();
    }

    private Response.Status statusOf(AccountErrorCode errorCode) {
        return switch (errorCode) {
            case ACCOUNT_ALREADY_EXISTS -> Response.Status.CONFLICT;       // 409
            case ACCOUNT_NOT_FOUND -> Response.Status.NOT_FOUND;           // 404
            case INVALID_ACCOUNT_DATA -> Response.Status.BAD_REQUEST;      // 400
            case ACCOUNT_ACCESS_DENIED -> Response.Status.FORBIDDEN;       // 403
        };
    }
}
