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
 * Menerjemahkan response error Keycloak menjadi exception domain.
 *
 * <p>
 * Ini batas antara "bahasa" OAuth2 dan bahasa aplikasi: mulai dari sini ke
 * atas, tidak ada lagi kode yang perlu tahu istilah {@code invalid_grant}.
 */
public class KeycloakErrorResponseMapper implements ResponseExceptionMapper<RuntimeException> {

    private static final Logger LOG = Logger.getLogger(KeycloakErrorResponseMapper.class);

    /**
     * Dibuat manual, bukan di-inject: provider rest client di-instansiasi oleh
     * klien itu sendiri, di luar konteks CDI.
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

        // 400 invalid_grant adalah balasan Keycloak untuk password salah;
        // 401 muncul kalau client_id / client_secret yang ditolak.
        if (status == 400 || status == 401) {
            return toAuthenticationException(error);
        }

        LOG.errorf("Keycloak returned HTTP %d (error=%s, description=%s)",
                status,
                error == null ? "-" : error.error(),
                error == null ? "-" : error.errorDescription());
        return new IdentityProviderUnavailableException(
                "Identity provider returned an unexpected response (HTTP " + status + ")");
    }

    private RuntimeException toAuthenticationException(KeycloakErrorResponse error) {
        String description = error == null || error.errorDescription() == null
                ? ""
                : error.errorDescription();
        String normalized = description.toLowerCase(Locale.ROOT);

        // Pesan brute force detection: "Account is temporarily disabled...".
        // Dicek lebih dulu karena juga mengandung kata "disabled".
        if (normalized.contains("temporarily disabled") || normalized.contains("temporarily locked")) {
            return new AccountLockedException(
                    "Account is temporarily locked because of too many failed login attempts");
        }
        if (normalized.contains("disabled") || normalized.contains("not fully set up")) {
            return new AccountDisabledException("Account is disabled or not fully set up");
        }

        // Jangan bocorkan apakah username-nya yang salah atau password-nya —
        // itu jalan pintas untuk enumerasi user.
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
