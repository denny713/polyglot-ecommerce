package com.ecommerce.auth.mapper;

import com.ecommerce.auth.dao.keycloak.KeycloakAdminErrorResponseMapper;
import com.ecommerce.auth.dao.keycloak.dto.KeycloakAdminErrorResponse;
import com.ecommerce.auth.enums.AccountErrorCode;
import com.ecommerce.auth.exception.AccountAlreadyExistsException;
import com.ecommerce.auth.exception.AccountNotFoundException;
import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import com.ecommerce.auth.exception.InvalidAccountDataException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the translation of Keycloak Admin REST API errors.
 *
 * <p>
 * Like the logout mapper, the point is that the statuses do <em>not</em> mean
 * what they mean on the token endpoint. The two that matter most are the ones
 * that must never be passed through: a 401 or 403 here is our service account
 * being rejected, and forwarding it would tell a caller their own perfectly
 * valid token was refused.
 */
class KeycloakAdminErrorResponseMapperTest {

    private final KeycloakAdminErrorResponseMapper mapper = new KeycloakAdminErrorResponseMapper();

    @Test
    void shouldHandleErrorStatusesOnly() {
        assertFalse(mapper.handles(200, null));
        assertFalse(mapper.handles(201, null));
        assertFalse(mapper.handles(204, null));
        assertTrue(mapper.handles(400, null));
        assertTrue(mapper.handles(409, null));
        assertTrue(mapper.handles(500, null));
    }

    @Test
    void shouldTranslateAConflictIntoATakenAccount() {
        RuntimeException thrown = mapper.toThrowable(errorResponse(409,
                """
                        {"errorMessage":"User exists with same username"}
                        """));

        AccountAlreadyExistsException taken = assertInstanceOf(AccountAlreadyExistsException.class, thrown);
        assertEquals(AccountErrorCode.ACCOUNT_ALREADY_EXISTS, taken.errorCode());
    }

    /**
     * Keycloak says which of the two collided. We deliberately do not pass that
     * on: "this email is registered" is a working account-enumeration oracle on
     * an endpoint that is open to anyone.
     */
    @Test
    void shouldNotDiscloseWhetherItWasTheUsernameOrTheEmail() {
        String message = mapper.toThrowable(errorResponse(409,
                """
                        {"errorMessage":"User exists with same email"}
                        """)).getMessage();

        assertEquals("An account with that username or email address already exists", message);
        assertFalse(message.contains("User exists"), "Keycloak's wording named the colliding field");
    }

    @Test
    void shouldTranslateABadRequestIntoRejectedAccountData() {
        RuntimeException thrown = mapper.toThrowable(errorResponse(400,
                """
                        {"errorMessage":"Password policy not met: length"}
                        """));

        InvalidAccountDataException rejected = assertInstanceOf(InvalidAccountDataException.class, thrown);
        assertEquals(AccountErrorCode.INVALID_ACCOUNT_DATA, rejected.errorCode());
        assertEquals("Password policy not met: length", rejected.getMessage(),
                "Keycloak's own wording is more useful than a guess at what it objected to");
    }

    @Test
    void shouldFallBackToAGenericMessageWhenA400CarriesNoDetail() {
        assertEquals("The identity provider rejected the account data",
                mapper.toThrowable(errorResponse(400, "")).getMessage());
    }

    @Test
    void shouldFallBackToAGenericMessageWhenA400BodyIsUnparseable() {
        assertInstanceOf(InvalidAccountDataException.class,
                mapper.toThrowable(errorResponse(400, "<html>Bad Request</html>")));
    }

    @Test
    void shouldFallBackToAGenericMessageWhenA400BodyIsNull() {
        assertInstanceOf(InvalidAccountDataException.class, mapper.toThrowable(errorResponse(400, null)));
    }

    @Test
    void shouldTranslateANotFoundIntoAMissingAccount() {
        RuntimeException thrown = mapper.toThrowable(errorResponse(404,
                """
                        {"error":"User not found"}
                        """));

        AccountNotFoundException missing = assertInstanceOf(AccountNotFoundException.class, thrown);
        assertEquals(AccountErrorCode.ACCOUNT_NOT_FOUND, missing.errorCode());
    }

    /**
     * Our service account token was refused. That is a deployment problem — most
     * likely the realm-management roles were never granted — and it must not
     * reach the caller as a 401 or 403 about their own token.
     */
    @Test
    void shouldTranslateARejectedServiceAccountIntoAnOutage() {
        RuntimeException thrown = mapper.toThrowable(errorResponse(403,
                """
                        {"error":"unknown_error"}
                        """));

        assertInstanceOf(IdentityProviderUnavailableException.class, thrown);
        assertEquals("Identity provider rejected the account request (HTTP 403)", thrown.getMessage());
    }

    @Test
    void shouldTranslateUnauthorizedIntoAnOutage() {
        assertInstanceOf(IdentityProviderUnavailableException.class,
                mapper.toThrowable(errorResponse(401, "")));
    }

    @Test
    void shouldTranslateServerErrorsIntoAnOutage() {
        assertEquals("Identity provider rejected the account request (HTTP 500)",
                mapper.toThrowable(errorResponse(500, "")).getMessage());
    }

    // ------------------------------------------------------------------
    // KeycloakAdminErrorResponse#description
    // ------------------------------------------------------------------

    @Test
    void shouldPreferTheAdminApiErrorMessageOverTheOAuthStyleField() {
        assertEquals("User exists with same username",
                new KeycloakAdminErrorResponse("conflict", "User exists with same username").description());
    }

    @Test
    void shouldFallBackToTheOAuthStyleFieldWhenThereIsNoErrorMessage() {
        assertEquals("HTTP 401 Unauthorized",
                new KeycloakAdminErrorResponse("HTTP 401 Unauthorized", null).description());
    }

    @Test
    void shouldFallBackToTheOAuthStyleFieldWhenTheErrorMessageIsBlank() {
        assertEquals("invalid_token", new KeycloakAdminErrorResponse("invalid_token", "  ").description());
    }

    @Test
    void shouldHaveNoDescriptionWhenBothFieldsAreMissing() {
        assertNull(new KeycloakAdminErrorResponse(null, null).description());
    }

    @Test
    void shouldHaveNoDescriptionWhenBothFieldsAreBlank() {
        assertNull(new KeycloakAdminErrorResponse(" ", "").description());
    }

    private Response errorResponse(int status, String body) {
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(status);
        when(response.readEntity(String.class)).thenReturn(body);
        return response;
    }
}
