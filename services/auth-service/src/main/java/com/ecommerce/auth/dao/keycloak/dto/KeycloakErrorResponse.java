package com.ecommerce.auth.dao.keycloak.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body error dari endpoint token Keycloak, mis.
 * {@code {"error":"invalid_grant","error_description":"Invalid user credentials"}}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakErrorResponse(
                @JsonProperty("error") String error,
                @JsonProperty("error_description") String errorDescription) {
}
