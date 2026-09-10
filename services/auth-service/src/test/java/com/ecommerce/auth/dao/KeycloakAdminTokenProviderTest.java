package com.ecommerce.auth.dao;

import com.ecommerce.auth.configuration.KeycloakAdminApiProperties;
import com.ecommerce.auth.configuration.KeycloakAuthProperties;
import com.ecommerce.auth.dao.keycloak.KeycloakAdminTokenClient;
import com.ecommerce.auth.dao.keycloak.KeycloakAdminTokenProvider;
import com.ecommerce.auth.dao.keycloak.dto.KeycloakTokenResponse;
import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for the service account token used on the Admin REST API.
 *
 * <p>
 * Two behaviours carry real weight here. The cache is what keeps a burst of
 * registrations from hammering the token endpoint hard enough to look like an
 * attack; and the translation of <em>any</em> failure into an outage is what
 * stops a wrong client secret in our own deployment from surfacing to a caller
 * as though their credentials were the problem.
 */
class KeycloakAdminTokenProviderTest {

    private KeycloakAdminTokenClient tokenClient;
    private KeycloakAdminApiProperties adminProperties;
    private KeycloakAdminTokenProvider provider;

    @BeforeEach
    void setUp() {
        tokenClient = mock(KeycloakAdminTokenClient.class);

        KeycloakAuthProperties authProperties = mock(KeycloakAuthProperties.class);
        when(authProperties.realm()).thenReturn("ecommerce");

        adminProperties = mock(KeycloakAdminApiProperties.class);
        when(adminProperties.clientId()).thenReturn("auth-service");
        when(adminProperties.clientSecret()).thenReturn("s3cr3t");
        when(adminProperties.grantType()).thenReturn("client_credentials");
        when(adminProperties.tokenExpiryLeewaySeconds()).thenReturn(30L);

        provider = new KeycloakAdminTokenProvider(tokenClient, authProperties, adminProperties);
    }

    @Test
    void shouldReturnTheTokenAsAReadyMadeAuthorizationHeader() {
        givenToken("service-account-token", 300);

        assertEquals("Bearer service-account-token", provider.bearerToken());
    }

    @Test
    void shouldRequestTheTokenFromTheConfiguredRealm() {
        givenToken("service-account-token", 300);

        provider.bearerToken();

        verify(tokenClient).requestToken(eq("ecommerce"), any());
    }

    @Test
    void shouldSendTheClientCredentialsFormKeycloakExpects() {
        givenToken("service-account-token", 300);
        provider.bearerToken();

        MultivaluedMap<String, String> form = capturedForm();

        assertEquals("client_credentials", form.getFirst("grant_type"));
        assertEquals("auth-service", form.getFirst("client_id"));
        assertEquals("s3cr3t", form.getFirst("client_secret"));
    }

    /**
     * A service account has no user behind it, so these two would be meaningless
     * — and sending a username on a client credentials grant is the kind of
     * copy-paste that quietly turns into a password grant.
     */
    @Test
    void shouldSendNoUsernameOrPassword() {
        givenToken("service-account-token", 300);
        provider.bearerToken();

        assertFalse(capturedForm().containsKey("username"));
        assertFalse(capturedForm().containsKey("password"));
    }

    @Test
    void shouldReuseAStillValidToken() {
        givenToken("service-account-token", 300);

        assertEquals(provider.bearerToken(), provider.bearerToken());
        verify(tokenClient, times(1)).requestToken(any(), any());
    }

    /**
     * The leeway is what stops a token from being sent on a request that outlives
     * it in flight: a token valid for less than the leeway is already treated as
     * spent.
     */
    @Test
    void shouldNotReuseATokenThatExpiresWithinTheLeeway() {
        givenToken("short-lived", 10);

        provider.bearerToken();
        provider.bearerToken();

        verify(tokenClient, times(2)).requestToken(any(), any());
    }

    @Test
    void shouldFetchAFreshTokenAfterOneExpires() {
        when(tokenClient.requestToken(any(), any()))
                .thenReturn(tokenResponse("first", 0))
                .thenReturn(tokenResponse("second", 300));

        assertEquals("Bearer first", provider.bearerToken());
        assertEquals("Bearer second", provider.bearerToken());
        assertEquals("Bearer second", provider.bearerToken(), "the fresh token should now be cached");
        verify(tokenClient, times(2)).requestToken(any(), any());
    }

    @Test
    void shouldTreatARejectedClientSecretAsAnOutageRatherThanA401() {
        WebApplicationException rejected = new WebApplicationException("invalid_client", 401);
        when(tokenClient.requestToken(any(), any())).thenThrow(rejected);

        IdentityProviderUnavailableException thrown = assertThrows(IdentityProviderUnavailableException.class,
                () -> provider.bearerToken());

        assertEquals("Could not obtain a service account token from the identity provider", thrown.getMessage());
        assertSame(rejected, thrown.getCause());
    }

    @Test
    void shouldTranslateTransportFailuresIntoAnOutage() {
        when(tokenClient.requestToken(any(), any())).thenThrow(new ProcessingException("connection refused"));

        assertThrows(IdentityProviderUnavailableException.class, () -> provider.bearerToken());
    }

    @Test
    void shouldRejectAResponseWithNoToken() {
        when(tokenClient.requestToken(any(), any())).thenReturn(null);

        assertEquals("The identity provider returned no service account token",
                assertThrows(IdentityProviderUnavailableException.class, () -> provider.bearerToken()).getMessage());
    }

    @Test
    void shouldRejectAResponseWhoseTokenIsNull() {
        when(tokenClient.requestToken(any(), any())).thenReturn(tokenResponse(null, 300));

        assertThrows(IdentityProviderUnavailableException.class, () -> provider.bearerToken());
    }

    @Test
    void shouldRejectAResponseWhoseTokenIsBlank() {
        when(tokenClient.requestToken(any(), any())).thenReturn(tokenResponse("   ", 300));

        assertThrows(IdentityProviderUnavailableException.class, () -> provider.bearerToken());
    }

    private void givenToken(String accessToken, long expiresIn) {
        when(tokenClient.requestToken(any(), any())).thenReturn(tokenResponse(accessToken, expiresIn));
    }

    private KeycloakTokenResponse tokenResponse(String accessToken, long expiresIn) {
        return new KeycloakTokenResponse(accessToken, null, "Bearer", expiresIn, 0, null);
    }

    private MultivaluedMap<String, String> capturedForm() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<MultivaluedMap<String, String>> captor = ArgumentCaptor.forClass(MultivaluedMap.class);
        verify(tokenClient).requestToken(eq("ecommerce"), captor.capture());
        return captor.getValue();
    }
}
