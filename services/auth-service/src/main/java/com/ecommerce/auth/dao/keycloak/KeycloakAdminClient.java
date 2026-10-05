package com.ecommerce.auth.dao.keycloak;

import com.ecommerce.auth.dao.keycloak.dto.KeycloakCredentialRepresentation;
import com.ecommerce.auth.dao.keycloak.dto.KeycloakRoleRepresentation;
import com.ecommerce.auth.dao.keycloak.dto.KeycloakUserRepresentation;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

/**
 * HTTP client for the Keycloak Admin REST API user endpoints — pure transport,
 * no business logic, exactly like {@link KeycloakTokenClient}.
 */
@Path("/admin/realms")
@RegisterRestClient(configKey = "keycloak-token-api")
@RegisterProvider(KeycloakAdminErrorResponseMapper.class)
public interface KeycloakAdminClient {

    /**
     * Answers HTTP 201 with an empty body and a {@code Location} header, hence
     * the {@code void} signature — the created account is read back with
     * {@link #findByUsername} so the id comes from Keycloak rather than from
     * parsing a URL.
     */
    @POST
    @Path("/{realm}/users")
    @Consumes(MediaType.APPLICATION_JSON)
    void createUser(@HeaderParam(HttpHeaders.AUTHORIZATION) String bearerToken,
            @PathParam("realm") String realm,
            KeycloakUserRepresentation user);

    /**
     * Exact-match lookup. Without {@code exact=true} Keycloak treats the
     * parameter as an infix search and would happily return somebody else's
     * account whose name merely contains this one.
     */
    @GET
    @Path("/{realm}/users")
    @Produces(MediaType.APPLICATION_JSON)
    List<KeycloakUserRepresentation> findByUsername(@HeaderParam(HttpHeaders.AUTHORIZATION) String bearerToken,
            @PathParam("realm") String realm,
            @QueryParam("username") String username,
            @QueryParam("exact") boolean exact);

    /**
     * Reads a single account. Answers HTTP 404 when the id is unknown, which
     * {@link KeycloakAdminErrorResponseMapper} turns into
     * {@code AccountNotFoundException}.
     */
    @GET
    @Path("/{realm}/users/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    KeycloakUserRepresentation findById(@HeaderParam(HttpHeaders.AUTHORIZATION) String bearerToken,
            @PathParam("realm") String realm,
            @PathParam("userId") String userId);

    /** Replaces the account's password and answers HTTP 204. */
    @PUT
    @Path("/{realm}/users/{userId}/reset-password")
    @Consumes(MediaType.APPLICATION_JSON)
    void resetPassword(@HeaderParam(HttpHeaders.AUTHORIZATION) String bearerToken,
            @PathParam("realm") String realm,
            @PathParam("userId") String userId,
            KeycloakCredentialRepresentation credential);

    /** Applies only the fields present in the body and answers HTTP 204. */
    @PUT
    @Path("/{realm}/users/{userId}")
    @Consumes(MediaType.APPLICATION_JSON)
    void updateUser(@HeaderParam(HttpHeaders.AUTHORIZATION) String bearerToken,
            @PathParam("realm") String realm,
            @PathParam("userId") String userId,
            KeycloakUserRepresentation user);

    /**
     * Reads one realm role by name, for the id a role mapping needs — see
     * {@link KeycloakRoleRepresentation}. Answers HTTP 404 when the realm has no
     * such role, which means the realm was not provisioned by
     * {@code app/init/keycloak-init.sh}.
     */
    @GET
    @Path("/{realm}/roles/{roleName}")
    @Produces(MediaType.APPLICATION_JSON)
    KeycloakRoleRepresentation findRealmRole(@HeaderParam(HttpHeaders.AUTHORIZATION) String bearerToken,
            @PathParam("realm") String realm,
            @PathParam("roleName") String roleName);

    /** Adds realm roles to an account and answers HTTP 204. */
    @POST
    @Path("/{realm}/users/{userId}/role-mappings/realm")
    @Consumes(MediaType.APPLICATION_JSON)
    void addRealmRoles(@HeaderParam(HttpHeaders.AUTHORIZATION) String bearerToken,
            @PathParam("realm") String realm,
            @PathParam("userId") String userId,
            List<KeycloakRoleRepresentation> roles);

    @DELETE
    @Path("/{realm}/users/{userId}")
    void deleteUser(@HeaderParam(HttpHeaders.AUTHORIZATION) String bearerToken,
            @PathParam("realm") String realm,
            @PathParam("userId") String userId);
}
