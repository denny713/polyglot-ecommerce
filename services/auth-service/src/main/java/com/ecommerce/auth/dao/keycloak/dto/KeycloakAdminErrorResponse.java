package com.ecommerce.auth.dao.keycloak.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Error body returned by the Keycloak Admin REST API, e.g.
 * {@code {"errorMessage":"User exists with same username"}}.
 *
 * <p>
 * A separate record from {@link KeycloakErrorResponse} because the two speak
 * different dialects: the token endpoint answers in OAuth2's
 * {@code error}/{@code error_description}, while the admin API uses its own
 * {@code errorMessage} — and, for a rejected bearer token, plain {@code error}.
 * Both are declared here so one mapper can read either.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakAdminErrorResponse(
        @JsonProperty("error") String error,
        @JsonProperty("errorMessage") String errorMessage) {

    /**
     * The most specific text Keycloak gave us, or {@code null} when it gave none.
     */
    public String description() {
        if (errorMessage != null && !errorMessage.isBlank()) {
            return errorMessage;
        }

        return error != null && !error.isBlank() ? error : null;
    }
}
