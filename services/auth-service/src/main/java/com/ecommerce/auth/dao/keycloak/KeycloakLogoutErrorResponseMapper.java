package com.ecommerce.auth.dao.keycloak;

import com.ecommerce.auth.dao.keycloak.dto.KeycloakErrorResponse;
import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import com.ecommerce.auth.exception.InvalidRefreshTokenException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;
import org.jboss.logging.Logger;

/**
 * Translates Keycloak end-session errors into domain exceptions.
 *
 * <p>
 * The same status codes mean something different here than they do on the token
 * endpoint, which is why this cannot be {@link KeycloakErrorResponseMapper}:
 *
 * <ul>
 * <li><strong>400 {@code invalid_grant}</strong> — the refresh token is expired,
 * unknown, or already revoked. Nothing is wrong with the caller's credentials;
 * the session it asked us to end simply no longer exists.</li>
 * <li><strong>401 {@code invalid_client}</strong> — <em>our</em> client id or
 * secret was rejected. That is a misconfiguration of this service, never the
 * user's fault, so it must not come back to the client as a 401.</li>
 * </ul>
 */
public class KeycloakLogoutErrorResponseMapper implements ResponseExceptionMapper<RuntimeException> {

    private static final Logger LOG = Logger.getLogger(KeycloakLogoutErrorResponseMapper.class);

    /**
     * Created manually rather than injected: rest client providers are
     * instantiated by the client itself, outside of the CDI context.
     */
    private static final ObjectMapper JSON = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Override
    public boolean handles(int status, MultivaluedMap<String, Object> headers) {
        return status >= 400;
    }

    @Override
    public RuntimeException toThrowable(Response response) {
        int status = response.getStatus();
        KeycloakErrorResponse error = readError(response);
        String code = error == null || error.error() == null ? "" : error.error();

        if (status == 400 && !"invalid_client".equals(code)) {
            return new InvalidRefreshTokenException("The refresh token is expired, unknown, or already revoked");
        }

        // Anything else is a problem on our side of the wire: a rejected client
        // credential, a 5xx, or an unreadable body.
        LOG.errorf("Keycloak end-session endpoint returned HTTP %d (error=%s, description=%s)",
                status,
                code.isEmpty() ? "-" : code,
                error == null ? "-" : error.errorDescription());

        return new IdentityProviderUnavailableException(
                "Identity provider rejected the logout request (HTTP " + status + ")");
    }

    private KeycloakErrorResponse readError(Response response) {
        try {
            String body = response.readEntity(String.class);
            if (body == null || body.isBlank()) {
                return null;
            }

            return JSON.readValue(body, KeycloakErrorResponse.class);
        } catch (Exception e) {
            LOG.debugf(e, "Could not parse the error body returned by Keycloak");
            return null;
        }
    }
}