package com.ecommerce.auth.handler;

import com.ecommerce.auth.dto.response.ErrorResponse;
import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Keycloak is down or not responding: 503, not 401. */
@Provider
public class IdentityProviderUnavailableExceptionMapper
        implements ExceptionMapper<IdentityProviderUnavailableException> {

    @Override
    public Response toResponse(IdentityProviderUnavailableException exception) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(ErrorResponse.of(
                        Response.Status.SERVICE_UNAVAILABLE.getStatusCode(),
                        "IDENTITY_PROVIDER_UNAVAILABLE",
                        "The authentication provider is currently unavailable, please try again later"))
                .build();
    }
}
