package com.ecommerce.auth.dao.keycloak.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Error body returned by the Keycloak Admin REST API, e.g.
 * {@code {"errorMessage":"User exists with same username"}}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakAdminErrorResponse(
        @JsonProperty("error") String error,
        @JsonProperty("errorMessage") String errorMessage) {

    /** The most specific text Keycloak gave us, or {@code null} when it gave none. */
    public String description() {
        if (errorMessage != null && !errorMessage.isBlank()) {
            return errorMessage;
        }

        return error != null && !error.isBlank() ? error : null;
    }
}
