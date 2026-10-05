package com.ecommerce.auth.mapper;

import com.ecommerce.auth.dao.keycloak.KeycloakLogoutErrorResponseMapper;
import com.ecommerce.auth.enums.AuthErrorCode;
import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import com.ecommerce.auth.exception.InvalidRefreshTokenException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit tests for the translation of Keycloak end-session errors. */
class KeycloakLogoutErrorResponseMapperTest {

    private final KeycloakLogoutErrorResponseMapper mapper = new KeycloakLogoutErrorResponseMapper();

    @Test
    void shouldHandleErrorStatusesOnly() {
        assertFalse(mapper.handles(200, null));
        assertFalse(mapper.handles(204, null));
        assertTrue(mapper.handles(400, null));
        assertTrue(mapper.handles(500, null));
    }

    @Test
    void shouldTranslateInvalidGrantIntoARejectedRefreshToken() {
        RuntimeException thrown = mapper.toThrowable(errorResponse(400,
                """
                        {"error":"invalid_grant","error_description":"Invalid refresh token"}
                        """));

        InvalidRefreshTokenException rejected = assertInstanceOf(InvalidRefreshTokenException.class, thrown);
        assertEquals(AuthErrorCode.INVALID_REFRESH_TOKEN, rejected.errorCode());
    }

    @Test
    void shouldTranslateAnUnparseable400IntoARejectedRefreshToken() {
        assertInstanceOf(InvalidRefreshTokenException.class,
                mapper.toThrowable(errorResponse(400, "<html>Bad Request</html>")));
    }

    @Test
    void shouldTranslateAnEmpty400BodyIntoARejectedRefreshToken() {
        assertInstanceOf(InvalidRefreshTokenException.class, mapper.toThrowable(errorResponse(400, "")));
    }

    @Test
    void shouldTranslateANull400BodyIntoARejectedRefreshToken() {
        assertInstanceOf(InvalidRefreshTokenException.class, mapper.toThrowable(errorResponse(400, null)));
    }

    /**
     * Our own client id or secret was rejected — a deployment problem. Reporting it
     * as a rejected refresh token would send the client off chasing a token that is
     * perfectly fine.
     */
    @Test
    void shouldTranslateARejectedClientIntoAnOutageEvenOn400() {
        RuntimeException thrown = mapper.toThrowable(errorResponse(400,
                """
                        {"error":"invalid_client","error_description":"Invalid client credentials"}
                        """));

        assertInstanceOf(IdentityProviderUnavailableException.class, thrown);
        assertEquals("Identity provider rejected the logout request (HTTP 400)", thrown.getMessage());
    }

    @Test
    void shouldTranslateUnauthorizedIntoAnOutage() {
        RuntimeException thrown = mapper.toThrowable(errorResponse(401,
                """
                        {"error":"invalid_client","error_description":"Client authentication failed"}
                        """));

        assertInstanceOf(IdentityProviderUnavailableException.class, thrown);
        assertEquals("Identity provider rejected the logout request (HTTP 401)", thrown.getMessage());
    }

    @Test
    void shouldTranslateServerErrorsIntoAnOutage() {
        assertInstanceOf(IdentityProviderUnavailableException.class,
                mapper.toThrowable(errorResponse(503, "")));
    }

    private Response errorResponse(int status, String body) {
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(status);
        when(response.readEntity(String.class)).thenReturn(body);
        return response;
    }
}