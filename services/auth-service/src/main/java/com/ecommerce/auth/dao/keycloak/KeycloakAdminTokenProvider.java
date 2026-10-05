package com.ecommerce.auth.dao.keycloak;

import com.ecommerce.auth.configuration.KeycloakAdminApiProperties;
import com.ecommerce.auth.configuration.KeycloakAuthProperties;
import com.ecommerce.auth.dao.keycloak.dto.KeycloakTokenResponse;
import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.Instant;

/**
 * Supplies the {@code Authorization} header value for Admin REST API calls,
 * obtained with the {@code client_credentials} grant on the {@code auth-service}
 * service account.
 */
@ApplicationScoped
public class KeycloakAdminTokenProvider {

    private static final Logger LOG = Logger.getLogger(KeycloakAdminTokenProvider.class);

    private final KeycloakAdminTokenClient tokenClient;
    private final KeycloakAuthProperties authProperties;
    private final KeycloakAdminApiProperties adminProperties;

    private volatile CachedToken cached;

    @Inject
    public KeycloakAdminTokenProvider(@RestClient KeycloakAdminTokenClient tokenClient,
                                      KeycloakAuthProperties authProperties,
                                      KeycloakAdminApiProperties adminProperties) {
        this.tokenClient = tokenClient;
        this.authProperties = authProperties;
        this.adminProperties = adminProperties;
    }

    /**
     * @return the full header value, {@code "Bearer <token>"}, ready to be passed
     *         to {@link KeycloakAdminClient}
     * @throws IdentityProviderUnavailableException the token could not be obtained
     */
    public String bearerToken() {
        CachedToken snapshot = cached;
        if (snapshot != null && snapshot.isUsableAt(Instant.now())) {
            return snapshot.header();
        }

        return refresh();
    }

    private synchronized String refresh() {
        // Re-check inside the lock: another thread may have refreshed while this
        // one waited, and a second token request would be pure waste.
        CachedToken snapshot = cached;
        if (snapshot != null && snapshot.isUsableAt(Instant.now())) {
            return snapshot.header();
        }

        KeycloakTokenResponse response = requestToken();
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new IdentityProviderUnavailableException(
                    "The identity provider returned no service account token");
        }

        CachedToken fresh = new CachedToken(
                "Bearer " + response.accessToken(),
                Instant.now()
                        .plusSeconds(response.expiresIn())
                        .minusSeconds(adminProperties.tokenExpiryLeewaySeconds()));

        cached = fresh;
        LOG.debugf("Obtained a service account token, usable until %s", fresh.usableUntil());
        return fresh.header();
    }

    private KeycloakTokenResponse requestToken() {
        try {
            return tokenClient.requestToken(authProperties.realm(), buildForm());
        } catch (ProcessingException | WebApplicationException e) {
            // A rejected client secret lands here too, as a 401 the rest client
            // turns into a WebApplicationException. That is a misconfiguration of
            // this service, so it is an outage from the caller's point of view —
            // never a 401 passed on to them.
            LOG.errorf(e, "Could not obtain a service account token for client '%s' in realm '%s'",
                    adminProperties.clientId(), authProperties.realm());
            throw new IdentityProviderUnavailableException(
                    "Could not obtain a service account token from the identity provider", e);
        }
    }

    private MultivaluedMap<String, String> buildForm() {
        MultivaluedMap<String, String> form = new MultivaluedHashMap<>();
        form.putSingle("grant_type", adminProperties.grantType());
        form.putSingle("client_id", adminProperties.clientId());
        form.putSingle("client_secret", adminProperties.clientSecret());
        return form;
    }

    /** A token together with the instant it stops being safe to send. */
    private record CachedToken(String header, Instant usableUntil) {

        boolean isUsableAt(Instant now) {
            return now.isBefore(usableUntil);
        }
    }
}
