package com.ecommerce.auth.dao.keycloak.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Error body returned by the Keycloak token endpoint, e.g.
 * {@code {"error":"invalid_grant","error_description":"Invalid user credentials"}}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakErrorResponse(
                @JsonProperty("error") String error,
                @JsonProperty("error_description") String errorDescription) {
}
