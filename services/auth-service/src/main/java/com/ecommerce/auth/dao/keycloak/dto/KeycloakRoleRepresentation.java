package com.ecommerce.auth.dao.keycloak.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Keycloak's {@code RoleRepresentation}, cut down to the two fields a role
 * mapping needs.
 *
 * <p>
 * The id is why this type exists at all. Assigning a realm role is
 * {@code POST /users/{id}/role-mappings/realm} with a list of roles, and
 * Keycloak resolves each one by <em>id</em>, not by name — so the role has to be
 * read back from the realm first, and the representation it answers with is
 * exactly what goes into the mapping call. Sending a name with a made-up or
 * absent id answers 404.
 *
 * @param id   the realm's id for the role, assigned when the role was created
 * @param name the role name, e.g. {@code user}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakRoleRepresentation(
        String id,
        String name) {
}
