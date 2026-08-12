package com.ecommerce.auth.dao.keycloak;

import com.ecommerce.auth.configuration.KeycloakAuthProperties;
import com.ecommerce.auth.dao.IdentityProviderDao;
import com.ecommerce.auth.exception.AuthenticationException;
import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import com.ecommerce.auth.mapper.KeycloakTokenMapper;
import com.ecommerce.auth.model.AuthToken;
import com.ecommerce.auth.model.UserCredentials;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/**
 * Implementation of {@link IdentityProviderDao} that uses Keycloak's Resource
 * Owner Password Credentials grant.
 *
 * <p>This is the only class in the entire service that knows the identity
 * provider in use is Keycloak. Switching providers means adding another
 * implementation of that interface, without changing the service or the
 * controller.
 */
@ApplicationScoped
public class KeycloakIdentityProviderDao implements IdentityProviderDao {

    private static final Logger LOG = Logger.getLogger(KeycloakIdentityProviderDao.class);

    private final KeycloakTokenClient tokenClient;
    private final KeycloakAuthProperties properties;
    private final KeycloakTokenMapper tokenMapper;

    @Inject
    public KeycloakIdentityProviderDao(@RestClient KeycloakTokenClient tokenClient,
                                       KeycloakAuthProperties properties,
                                       KeycloakTokenMapper tokenMapper) {
        this.tokenClient = tokenClient;
        this.properties = properties;
        this.tokenMapper = tokenMapper;
    }

    @Override
    public AuthToken authenticate(UserCredentials credentials) {
        try {
            return tokenMapper.toDomain(tokenClient.requestToken(properties.realm(), buildForm(credentials)));
        } catch (AuthenticationException | IdentityProviderUnavailableException e) {
            // Already translated by KeycloakErrorResponseMapper — rethrow as-is.
            throw e;
        } catch (ProcessingException | WebApplicationException e) {
            // Connection refused, a timeout, or a body that could not be read.
            LOG.errorf(e, "Failed to reach the Keycloak token endpoint for realm '%s'", properties.realm());
            throw new IdentityProviderUnavailableException("Could not reach the identity provider", e);
        }
    }

    /**
     * Builds the {@code application/x-www-form-urlencoded} body exactly as the
     * OAuth2 specification requires for the password grant.
     */
    private MultivaluedMap<String, String> buildForm(UserCredentials credentials) {
        MultivaluedMap<String, String> form = new MultivaluedHashMap<>();
        form.putSingle("grant_type", properties.grantType());
        form.putSingle("client_id", properties.clientId());
        form.putSingle("username", credentials.username());
        form.putSingle("password", credentials.password());

        // A public client such as ecommerce-app has no secret; a confidential
        // client is required to send one.
        properties.clientSecret()
                .filter(secret -> !secret.isBlank())
                .ifPresent(secret -> form.putSingle("client_secret", secret));

        properties.scope()
                .filter(scope -> !scope.isBlank())
                .ifPresent(scope -> form.putSingle("scope", scope));

        return form;
    }
}
