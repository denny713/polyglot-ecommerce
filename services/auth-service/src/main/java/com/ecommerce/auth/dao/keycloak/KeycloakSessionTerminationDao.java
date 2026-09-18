package com.ecommerce.auth.dao.keycloak;

import com.ecommerce.auth.configuration.KeycloakAuthProperties;
import com.ecommerce.auth.dao.SessionTerminationDao;
import com.ecommerce.auth.exception.AuthenticationException;
import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import com.ecommerce.auth.model.RefreshToken;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/** Implementation of {@link SessionTerminationDao} that calls Keycloak's OpenID Connect end-session endpoint. */
@ApplicationScoped
public class KeycloakSessionTerminationDao implements SessionTerminationDao {

    private static final Logger LOG = Logger.getLogger(KeycloakSessionTerminationDao.class);

    private final KeycloakLogoutClient logoutClient;
    private final KeycloakAuthProperties properties;

    @Inject
    public KeycloakSessionTerminationDao(@RestClient KeycloakLogoutClient logoutClient,
                                         KeycloakAuthProperties properties) {
        this.logoutClient = logoutClient;
        this.properties = properties;
    }

    @Override
    public void revoke(RefreshToken refreshToken) {
        try {
            logoutClient.logout(properties.realm(), buildForm(refreshToken));
        } catch (AuthenticationException | IdentityProviderUnavailableException e) {
            // Already translated by KeycloakLogoutErrorResponseMapper — rethrow as-is.
            throw e;
        } catch (ProcessingException | WebApplicationException e) {
            // Connection refused, a timeout, or a body that could not be read.
            LOG.errorf(e, "Failed to reach the Keycloak end-session endpoint for realm '%s'", properties.realm());
            throw new IdentityProviderUnavailableException("Could not reach the identity provider", e);
        }
    }

    /**
     * Builds the {@code application/x-www-form-urlencoded} body the end-session
     * endpoint expects. There is no {@code grant_type} here — logout is not a
     * grant.
     */
    private MultivaluedMap<String, String> buildForm(RefreshToken refreshToken) {
        MultivaluedMap<String, String> form = new MultivaluedHashMap<>();
        form.putSingle("client_id", properties.clientId());
        form.putSingle("refresh_token", refreshToken.value());

        // A public client such as ecommerce-app has no secret; a confidential
        // client is required to send one.
        properties.clientSecret()
                .filter(secret -> !secret.isBlank())
                .ifPresent(secret -> form.putSingle("client_secret", secret));

        return form;
    }
}