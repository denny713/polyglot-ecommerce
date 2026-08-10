package com.ecommerce.auth.dao.keycloak;

import com.ecommerce.auth.config.KeycloakAuthProperties;
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
 * Implementasi {@link IdentityProviderDao} yang memakai Resource Owner Password
 * Credentials grant milik Keycloak.
 *
 * <p>Hanya kelas inilah — di seluruh service — yang tahu bahwa identity provider
 * yang dipakai adalah Keycloak. Mengganti provider berarti menambah implementasi
 * lain dari interface tersebut, tanpa mengubah service maupun controller.
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
            return tokenMapper.toDomain(
                    tokenClient.requestToken(properties.realm(), buildForm(credentials)));
        } catch (AuthenticationException | IdentityProviderUnavailableException e) {
            // Sudah diterjemahkan KeycloakErrorResponseMapper — teruskan apa adanya.
            throw e;
        } catch (ProcessingException | WebApplicationException e) {
            // Connection refused, timeout, atau body yang tidak bisa dibaca.
            LOG.errorf(e, "Failed to reach the Keycloak token endpoint for realm '%s'",
                    properties.realm());
            throw new IdentityProviderUnavailableException(
                    "Could not reach the identity provider", e);
        }
    }

    /**
     * Menyusun body {@code application/x-www-form-urlencoded} persis seperti
     * yang diminta spesifikasi OAuth2 untuk password grant.
     */
    private MultivaluedMap<String, String> buildForm(UserCredentials credentials) {
        MultivaluedMap<String, String> form = new MultivaluedHashMap<>();
        form.putSingle("grant_type", properties.grantType());
        form.putSingle("client_id", properties.clientId());
        form.putSingle("username", credentials.username());
        form.putSingle("password", credentials.password());

        // Public client seperti ecommerce-app tidak punya secret; confidential
        // client wajib mengirimkannya.
        properties.clientSecret()
                .filter(secret -> !secret.isBlank())
                .ifPresent(secret -> form.putSingle("client_secret", secret));

        properties.scope()
                .filter(scope -> !scope.isBlank())
                .ifPresent(scope -> form.putSingle("scope", scope));

        return form;
    }
}
