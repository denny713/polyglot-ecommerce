package com.ecommerce.auth.dao.keycloak.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Keycloak's {@code CredentialRepresentation}, as sent inline with a new user so
 * that creating the account and setting its password are one call rather than
 * two — a second call could fail on its own and leave a passwordless account
 * behind.
 *
 * @param type      always {@code password} here
 * @param value     the plain password; Keycloak hashes it and applies the realm
 *                  password policy
 * @param temporary {@code false}, so the user is not forced to change it at
 *                  first login
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KeycloakCredentialRepresentation(
        String type,
        String value,
        Boolean temporary) {

    public static KeycloakCredentialRepresentation permanentPassword(String password) {
        return new KeycloakCredentialRepresentation("password", password, false);
    }

    /**
     * The password is never printed, not even when a request is logged.
     */
    @Override
    public String toString() {
        return "KeycloakCredentialRepresentation[type=" + type + ", value=***, temporary=" + temporary + "]";
    }
}
