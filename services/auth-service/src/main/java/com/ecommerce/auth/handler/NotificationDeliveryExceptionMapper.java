package com.ecommerce.auth.handler;

import com.ecommerce.auth.dto.response.ErrorResponse;
import com.ecommerce.auth.exception.NotificationDeliveryException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * The mail server is down or refusing us: 503, and a code of its own.
 *
 * <p>
 * It would be easy to fold this into {@code IDENTITY_PROVIDER_UNAVAILABLE} —
 * both are 503 — but the two send an operator to different places, and a client
 * retrying a registration deserves to know that Keycloak was fine and the mail
 * was not.
 */
@Provider
public class NotificationDeliveryExceptionMapper implements ExceptionMapper<NotificationDeliveryException> {

    @Override
    public Response toResponse(NotificationDeliveryException exception) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(ErrorResponse.of(
                        Response.Status.SERVICE_UNAVAILABLE.getStatusCode(),
                        "NOTIFICATION_UNAVAILABLE",
                        "The account could not be created because the email carrying its password "
                                + "could not be sent, please try again later"))
                .build();
    }
}
