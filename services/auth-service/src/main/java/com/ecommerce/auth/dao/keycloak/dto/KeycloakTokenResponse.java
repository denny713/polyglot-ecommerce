package com.ecommerce.auth.dao.keycloak.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Success body returned by {@code POST /realms/{realm}/protocol/openid-connect/token}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakTokenResponse(
                @JsonProperty("access_token") String accessToken,
                @JsonProperty("refresh_token") String refreshToken,
                @JsonProperty("token_type") String tokenType,
                @JsonProperty("expires_in") long expiresIn,
                @JsonProperty("refresh_expires_in") long refreshExpiresIn,
                @JsonProperty("scope") String scope) {
}
