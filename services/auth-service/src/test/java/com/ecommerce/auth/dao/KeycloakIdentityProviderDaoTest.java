package com.ecommerce.auth.dao;

import com.ecommerce.auth.configuration.KeycloakAuthProperties;
import com.ecommerce.auth.dao.keycloak.KeycloakIdentityProviderDao;
import com.ecommerce.auth.dao.keycloak.KeycloakTokenClient;
import com.ecommerce.auth.dao.keycloak.dto.KeycloakTokenResponse;
import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import com.ecommerce.auth.exception.InvalidCredentialsException;
import com.ecommerce.auth.mapper.KeycloakTokenMapper;
import com.ecommerce.auth.model.AuthToken;
import com.ecommerce.auth.model.UserCredentials;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for the login DAO. */
class KeycloakIdentityProviderDaoTest {

    private static final UserCredentials CREDENTIALS = new UserCredentials("adminapp", "P@ssw0rd");

    private KeycloakTokenClient tokenClient;
    private KeycloakAuthProperties properties;
    private KeycloakIdentityProviderDao dao;

    @BeforeEach
    void setUp() {
        tokenClient = mock(KeycloakTokenClient.class);
        properties = mock(KeycloakAuthProperties.class);

        when(properties.realm()).thenReturn("ecommerce");
        when(properties.clientId()).thenReturn("ecommerce-app");
        when(properties.grantType()).thenReturn("password");
        when(properties.clientSecret()).thenReturn(Optional.empty());
        when(properties.scope()).thenReturn(Optional.empty());

        dao = new KeycloakIdentityProviderDao(tokenClient, properties, new KeycloakTokenMapper());
    }

    @Test
    void shouldReturnTheMappedTokenOnSuccess() {
        when(tokenClient.requestToken(eq("ecommerce"), any()))
                .thenReturn(new KeycloakTokenResponse("access", "refresh", "Bearer", 300, 1800, "profile"));

        AuthToken token = dao.authenticate(CREDENTIALS);

        assertEquals("access", token.accessToken());
        assertEquals("refresh", token.refreshToken());
        assertEquals(300L, token.expiresInSeconds());
    }

    @Test
    void shouldSendThePasswordGrantFormKeycloakExpects() {
        MultivaluedMap<String, String> form = captureForm();

        assertEquals("password", form.getFirst("grant_type"));
        assertEquals("ecommerce-app", form.getFirst("client_id"));
        assertEquals("adminapp", form.getFirst("username"));
        assertEquals("P@ssw0rd", form.getFirst("password"));
    }

    /**
     * A public client such as {@code ecommerce-app} has no secret; sending an empty
     * one would make Keycloak reject the request as an invalid client.
     */
    @Test
    void shouldOmitTheClientSecretWhenTheClientIsPublic() {
        assertFalse(captureForm().containsKey("client_secret"));
    }

    @Test
    void shouldOmitTheClientSecretWhenItIsConfiguredButBlank() {
        when(properties.clientSecret()).thenReturn(Optional.of("   "));

        assertFalse(captureForm().containsKey("client_secret"));
    }

    @Test
    void shouldSendTheClientSecretForAConfidentialClient() {
        when(properties.clientSecret()).thenReturn(Optional.of("s3cr3t"));

        assertEquals("s3cr3t", captureForm().getFirst("client_secret"));
    }

    @Test
    void shouldOmitTheScopeWhenItIsNotConfigured() {
        assertFalse(captureForm().containsKey("scope"));
    }

    @Test
    void shouldOmitTheScopeWhenItIsConfiguredButBlank() {
        when(properties.scope()).thenReturn(Optional.of(""));

        assertFalse(captureForm().containsKey("scope"));
    }

    @Test
    void shouldSendTheConfiguredScope() {
        when(properties.scope()).thenReturn(Optional.of("profile email"));

        assertEquals("profile email", captureForm().getFirst("scope"));
    }

    /**
     * {@code KeycloakErrorResponseMapper} has already turned the HTTP failure into a
     * domain exception; wrapping it again would hide the 401 behind a 503.
     */
    @Test
    void shouldRethrowAlreadyTranslatedAuthenticationFailures() {
        InvalidCredentialsException rejected = new InvalidCredentialsException("Invalid username or password");
        when(tokenClient.requestToken(any(), any())).thenThrow(rejected);

        assertSame(rejected, assertThrows(InvalidCredentialsException.class, () -> dao.authenticate(CREDENTIALS)));
    }

    @Test
    void shouldRethrowAlreadyTranslatedOutages() {
        IdentityProviderUnavailableException down = new IdentityProviderUnavailableException("HTTP 500");
        when(tokenClient.requestToken(any(), any())).thenThrow(down);

        assertSame(down, assertThrows(IdentityProviderUnavailableException.class, () -> dao.authenticate(CREDENTIALS)));
    }

    /**
     * Connection refused or a read timeout: no HTTP response ever arrived, so no
     * response mapper ran and the DAO has to translate it itself.
     */
    @Test
    void shouldTranslateTransportFailuresIntoAnOutage() {
        ProcessingException transportFailure = new ProcessingException("connection refused");
        when(tokenClient.requestToken(any(), any())).thenThrow(transportFailure);

        IdentityProviderUnavailableException thrown = assertThrows(IdentityProviderUnavailableException.class,
                () -> dao.authenticate(CREDENTIALS));

        assertEquals("Could not reach the identity provider", thrown.getMessage());
        assertSame(transportFailure, thrown.getCause());
    }

    @Test
    void shouldTranslateUnmappedHttpFailuresIntoAnOutage() {
        when(tokenClient.requestToken(any(), any())).thenThrow(new WebApplicationException("unreadable body"));

        assertThrows(IdentityProviderUnavailableException.class, () -> dao.authenticate(CREDENTIALS));
    }

    /** Runs a successful authentication and hands back the form that was actually put on the wire. */
    private MultivaluedMap<String, String> captureForm() {
        when(tokenClient.requestToken(any(), any()))
                .thenReturn(new KeycloakTokenResponse("access", "refresh", "Bearer", 300, 1800, "profile"));

        dao.authenticate(CREDENTIALS);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<MultivaluedMap<String, String>> captor = ArgumentCaptor.forClass(MultivaluedMap.class);
        verify(tokenClient).requestToken(eq("ecommerce"), captor.capture());
        return captor.getValue();
    }
}