package com.ecommerce.auth.mapper;

import com.ecommerce.auth.dao.keycloak.KeycloakErrorResponseMapper;
import com.ecommerce.auth.exception.AccountDisabledException;
import com.ecommerce.auth.exception.AccountLockedException;
import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import com.ecommerce.auth.exception.InvalidCredentialsException;
import com.ecommerce.auth.enums.AuthErrorCode;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit tests for the translation of Keycloak token-endpoint errors into domain exceptions. */
class KeycloakErrorResponseMapperTest {

    private final KeycloakErrorResponseMapper mapper = new KeycloakErrorResponseMapper();

    @Test
    void shouldHandleErrorStatusesOnly() {
        assertFalse(mapper.handles(200, null));
        assertFalse(mapper.handles(204, null));
        assertTrue(mapper.handles(400, null));
        assertTrue(mapper.handles(401, null));
        assertTrue(mapper.handles(500, null));
    }

    @Test
    void shouldTranslateInvalidGrantIntoInvalidCredentials() {
        RuntimeException thrown = mapper.toThrowable(errorResponse(400,
                """
                        {"error":"invalid_grant","error_description":"Invalid user credentials"}
                        """));

        InvalidCredentialsException invalid = assertInstanceOf(InvalidCredentialsException.class, thrown);
        assertEquals(AuthErrorCode.INVALID_CREDENTIALS, invalid.errorCode());
        // Never reveal which half of the credentials was wrong.
        assertEquals("Invalid username or password", invalid.getMessage());
    }

    @Test
    void shouldTranslateARejectedClientIntoInvalidCredentials() {
        RuntimeException thrown = mapper.toThrowable(errorResponse(401,
                """
                        {"error":"invalid_client","error_description":"Invalid client credentials"}
                        """));

        assertInstanceOf(InvalidCredentialsException.class, thrown);
    }

    /**
     * Brute force detection. Checked before the "disabled" rule because Keycloak's
     * message — "Account is temporarily disabled" — contains that word too.
     */
    @Test
    void shouldTranslateATemporarilyDisabledAccountIntoAccountLocked() {
        RuntimeException thrown = mapper.toThrowable(errorResponse(400,
                """
                        {"error":"invalid_grant","error_description":"Account is temporarily disabled, contact your administrator"}
                        """));

        AccountLockedException locked = assertInstanceOf(AccountLockedException.class, thrown);
        assertEquals(AuthErrorCode.ACCOUNT_LOCKED, locked.errorCode());
    }

    @Test
    void shouldTranslateATemporarilyLockedAccountIntoAccountLocked() {
        RuntimeException thrown = mapper.toThrowable(errorResponse(400,
                """
                        {"error":"invalid_grant","error_description":"Account is temporarily locked"}
                        """));

        assertInstanceOf(AccountLockedException.class, thrown);
    }

    @Test
    void shouldTranslateADisabledAccountIntoAccountDisabled() {
        RuntimeException thrown = mapper.toThrowable(errorResponse(400,
                """
                        {"error":"invalid_grant","error_description":"Account disabled"}
                        """));

        AccountDisabledException disabled = assertInstanceOf(AccountDisabledException.class, thrown);
        assertEquals(AuthErrorCode.ACCOUNT_DISABLED, disabled.errorCode());
    }

    @Test
    void shouldTranslatePendingRequiredActionsIntoAccountDisabled() {
        RuntimeException thrown = mapper.toThrowable(errorResponse(400,
                """
                        {"error":"invalid_grant","error_description":"Account is not fully set up"}
                        """));

        assertInstanceOf(AccountDisabledException.class, thrown);
    }

    /**
     * The description is matched case-insensitively, so a future Keycloak release
     * capitalising it differently must not silently downgrade a locked account to
     * "wrong password".
     */
    @Test
    void shouldMatchTheDescriptionRegardlessOfCase() {
        RuntimeException thrown = mapper.toThrowable(errorResponse(400,
                """
                        {"error":"invalid_grant","error_description":"ACCOUNT IS TEMPORARILY DISABLED"}
                        """));

        assertInstanceOf(AccountLockedException.class, thrown);
    }

    @Test
    void shouldFallBackToInvalidCredentialsWhenTheDescriptionIsMissing() {
        RuntimeException thrown = mapper.toThrowable(errorResponse(400,
                """
                        {"error":"invalid_grant"}
                        """));

        assertInstanceOf(InvalidCredentialsException.class, thrown);
    }

    @Test
    void shouldTranslateServerErrorsIntoAnOutage() {
        RuntimeException thrown = mapper.toThrowable(errorResponse(500,
                """
                        {"error":"server_error","error_description":"boom"}
                        """));

        assertInstanceOf(IdentityProviderUnavailableException.class, thrown);
        assertEquals("Identity provider returned an unexpected response (HTTP 500)", thrown.getMessage());
    }

    @Test
    void shouldTranslateAnEmptyServerErrorBodyIntoAnOutage() {
        assertInstanceOf(IdentityProviderUnavailableException.class, mapper.toThrowable(errorResponse(503, "")));
    }

    /**
     * A proxy in front of Keycloak may answer with an HTML error page. An
     * unparseable body must not become an unparseable stack trace.
     */
    @Test
    void shouldFallBackWhenTheBodyIsNotJson() {
        assertInstanceOf(InvalidCredentialsException.class,
                mapper.toThrowable(errorResponse(400, "<html>Bad Request</html>")));
    }

    @Test
    void shouldFallBackWhenTheBodyIsNull() {
        assertInstanceOf(InvalidCredentialsException.class, mapper.toThrowable(errorResponse(400, null)));
    }

    /**
     * A {@code Response} built on the server side refuses {@code readEntity}, so the
     * inbound message is mocked instead — this is the shape the rest client hands
     * the mapper at runtime.
     */
    private Response errorResponse(int status, String body) {
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(status);
        when(response.readEntity(String.class)).thenReturn(body);
        return response;
    }
}