package com.ecommerce.auth.dao.keycloak;

import com.ecommerce.auth.dao.keycloak.dto.KeycloakErrorResponse;
import com.ecommerce.auth.exception.AccountDisabledException;
import com.ecommerce.auth.exception.AccountLockedException;
import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import com.ecommerce.auth.exception.InvalidCredentialsException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;
import org.jboss.logging.Logger;

import java.util.Locale;

/**
 * Translates Keycloak error responses into domain exceptions.
 *
 * <p>
 * This is the boundary between the "language" of OAuth2 and the language of the
 * application: from here upwards, no code needs to know the term
 * {@code invalid_grant}.
 */
public class KeycloakErrorResponseMapper implements ResponseExceptionMapper<RuntimeException> {

    private static final Logger LOG = Logger.getLogger(KeycloakErrorResponseMapper.class);

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

        // A 400 invalid_grant is how Keycloak reports a wrong password;
        // a 401 shows up when the client_id / client_secret is the rejected part.
        if (status == 400 || status == 401) {
            return toAuthenticationException(error);
        }

        LOG.errorf("Keycloak returned HTTP %d (error=%s, description=%s)",
                status,
                error == null ? "-" : error.error(),
                error == null ? "-" : error.errorDescription()
        );

        return new IdentityProviderUnavailableException(
                "Identity provider returned an unexpected response (HTTP " + status + ")");
    }

    private RuntimeException toAuthenticationException(KeycloakErrorResponse error) {
        String description = error == null || error.errorDescription() == null
                ? "" : error.errorDescription();
        String normalized = description.toLowerCase(Locale.ROOT);

        // The brute force detection message: "Account is temporarily disabled...".
        // Checked first because it also contains the word "disabled".
        if (normalized.contains("temporarily disabled") || normalized.contains("temporarily locked")) {
            return new AccountLockedException(
                    "Account is temporarily locked because of too many failed login attempts");
        }

        if (normalized.contains("disabled") || normalized.contains("not fully set up")) {
            return new AccountDisabledException("Account is disabled or not fully set up");
        }

        // Never reveal whether it was the username or the password that was wrong —
        // that is a shortcut to user enumeration.
        return new InvalidCredentialsException("Invalid username or password");
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
