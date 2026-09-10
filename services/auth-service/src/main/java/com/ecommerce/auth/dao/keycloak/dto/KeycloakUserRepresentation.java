package com.ecommerce.auth.dao.keycloak.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Keycloak's {@code UserRepresentation}, cut down to the fields this service
 * sends or reads back.
 *
 * <p>
 * {@link JsonInclude.Include#NON_NULL} is what makes a partial update work:
 * Keycloak's {@code PUT /admin/realms/{realm}/users/{id}} applies only the
 * fields present in the body, so leaving {@code email} out means "keep the one
 * you have" while sending {@code null} explicitly would be a request to clear
 * it. Serializing every field would turn every update into a full overwrite.
 *
 * <p>
 * The boxed {@link Boolean}s exist for the same reason — {@code false} and
 * "not mentioned" have to stay distinguishable.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakUserRepresentation(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        Boolean enabled,
        Boolean emailVerified,
        List<String> requiredActions,
        List<KeycloakCredentialRepresentation> credentials) {
}
