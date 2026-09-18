package com.ecommerce.auth.dao.keycloak.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Keycloak's {@code RoleRepresentation}, cut down to the two fields a role
 * mapping needs.
 * @param id   the realm's id for the role, assigned when the role was created
 * @param name the role name, e.g. {@code user}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakRoleRepresentation(
        String id,
        String name) {
}
