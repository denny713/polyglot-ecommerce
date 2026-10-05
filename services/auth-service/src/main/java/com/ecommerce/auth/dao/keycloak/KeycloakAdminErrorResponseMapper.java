package com.ecommerce.auth.dao.keycloak;

import com.ecommerce.auth.dao.keycloak.dto.KeycloakAdminErrorResponse;
import com.ecommerce.auth.exception.AccountAlreadyExistsException;
import com.ecommerce.auth.exception.AccountNotFoundException;
import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import com.ecommerce.auth.exception.InvalidAccountDataException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;
import org.jboss.logging.Logger;

/**
 * Translates Keycloak Admin REST API errors into domain exceptions — the
 * account-management counterpart of {@link KeycloakErrorResponseMapper}.
 */
public class KeycloakAdminErrorResponseMapper implements ResponseExceptionMapper<RuntimeException> {

    private static final Logger LOG = Logger.getLogger(KeycloakAdminErrorResponseMapper.class);

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
        KeycloakAdminErrorResponse error = readError(response);
        String description = error == null ? null : error.description();

        return switch (status) {
            case 400 -> new InvalidAccountDataException(
                    description == null ? "The identity provider rejected the account data" : description);
            case 404 -> new AccountNotFoundException("No account exists with that id");
            // Never says whether it was the username or the email that collided:
            // that answer is a user enumeration oracle.
            case 409 -> new AccountAlreadyExistsException(
                    "An account with that username or email address already exists");
            default -> unavailable(status, description);
        };
    }

    /**
     * Everything left over is a problem on our side of the wire: a rejected or
     * under-privileged service account token, a 5xx, or an unreadable body. It is
     * logged at {@code ERROR} because nobody but an operator can act on it.
     */
    private IdentityProviderUnavailableException unavailable(int status, String description) {
        LOG.errorf("Keycloak admin API returned HTTP %d (%s)%s",
                status,
                description == null ? "no detail" : description,
                status == 401 || status == 403
                        ? " — check that the auth-service service account holds the realm-management roles"
                        : "");

        return new IdentityProviderUnavailableException(
                "Identity provider rejected the account request (HTTP " + status + ")");
    }

    private KeycloakAdminErrorResponse readError(Response response) {
        try {
            String body = response.readEntity(String.class);
            if (body == null || body.isBlank()) {
                return null;
            }

            return JSON.readValue(body, KeycloakAdminErrorResponse.class);
        } catch (Exception e) {
            LOG.debugf(e, "Could not parse the error body returned by the Keycloak admin API");
            return null;
        }
    }
}
