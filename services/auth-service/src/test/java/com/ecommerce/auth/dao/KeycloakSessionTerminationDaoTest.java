package com.ecommerce.auth.dao;

import com.ecommerce.auth.configuration.KeycloakAuthProperties;
import com.ecommerce.auth.dao.keycloak.KeycloakLogoutClient;
import com.ecommerce.auth.dao.keycloak.KeycloakSessionTerminationDao;
import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import com.ecommerce.auth.exception.InvalidRefreshTokenException;
import com.ecommerce.auth.model.RefreshToken;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for the logout DAO. */
class KeycloakSessionTerminationDaoTest {

    private static final RefreshToken REFRESH_TOKEN = new RefreshToken("refresh-token");

    private KeycloakLogoutClient logoutClient;
    private KeycloakAuthProperties properties;
    private KeycloakSessionTerminationDao dao;

    @BeforeEach
    void setUp() {
        logoutClient = mock(KeycloakLogoutClient.class);
        properties = mock(KeycloakAuthProperties.class);

        when(properties.realm()).thenReturn("ecommerce");
        when(properties.clientId()).thenReturn("ecommerce-app");
        when(properties.clientSecret()).thenReturn(Optional.empty());

        dao = new KeycloakSessionTerminationDao(logoutClient, properties);
    }

    @Test
    void shouldCallTheEndSessionEndpointOfTheConfiguredRealm() {
        dao.revoke(REFRESH_TOKEN);

        verify(logoutClient).logout(eq("ecommerce"), any());
    }

    @Test
    void shouldSendTheEndSessionFormKeycloakExpects() {
        MultivaluedMap<String, String> form = captureForm();

        assertEquals("ecommerce-app", form.getFirst("client_id"));
        assertEquals("refresh-token", form.getFirst("refresh_token"));
    }

    /** Logout is not a grant — sending {@code grant_type} here would be copied-over noise from the token endpoint. */
    @Test
    void shouldNotSendAGrantType() {
        assertFalse(captureForm().containsKey("grant_type"));
    }

    @Test
    void shouldOmitTheClientSecretWhenTheClientIsPublic() {
        assertFalse(captureForm().containsKey("client_secret"));
    }

    @Test
    void shouldOmitTheClientSecretWhenItIsConfiguredButBlank() {
        when(properties.clientSecret()).thenReturn(Optional.of("  "));

        assertFalse(captureForm().containsKey("client_secret"));
    }

    @Test
    void shouldSendTheClientSecretForAConfidentialClient() {
        when(properties.clientSecret()).thenReturn(Optional.of("s3cr3t"));

        assertEquals("s3cr3t", captureForm().getFirst("client_secret"));
    }

    @Test
    void shouldRethrowAnAlreadyTranslatedRejectedToken() {
        InvalidRefreshTokenException rejected = new InvalidRefreshTokenException("expired");
        doThrow(rejected).when(logoutClient).logout(any(), any());

        assertSame(rejected, assertThrows(InvalidRefreshTokenException.class, () -> dao.revoke(REFRESH_TOKEN)));
    }

    @Test
    void shouldRethrowAlreadyTranslatedOutages() {
        IdentityProviderUnavailableException down = new IdentityProviderUnavailableException("HTTP 500");
        doThrow(down).when(logoutClient).logout(any(), any());

        assertSame(down, assertThrows(IdentityProviderUnavailableException.class, () -> dao.revoke(REFRESH_TOKEN)));
    }

    @Test
    void shouldTranslateTransportFailuresIntoAnOutage() {
        ProcessingException transportFailure = new ProcessingException("connection refused");
        doThrow(transportFailure).when(logoutClient).logout(any(), any());

        IdentityProviderUnavailableException thrown = assertThrows(IdentityProviderUnavailableException.class,
                () -> dao.revoke(REFRESH_TOKEN));

        assertEquals("Could not reach the identity provider", thrown.getMessage());
        assertSame(transportFailure, thrown.getCause());
    }

    @Test
    void shouldTranslateUnmappedHttpFailuresIntoAnOutage() {
        doThrow(new WebApplicationException("unreadable body")).when(logoutClient).logout(any(), any());

        assertThrows(IdentityProviderUnavailableException.class, () -> dao.revoke(REFRESH_TOKEN));
    }

    private MultivaluedMap<String, String> captureForm() {
        dao.revoke(REFRESH_TOKEN);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<MultivaluedMap<String, String>> captor = ArgumentCaptor.forClass(MultivaluedMap.class);
        verify(logoutClient).logout(eq("ecommerce"), captor.capture());
        return captor.getValue();
    }
}