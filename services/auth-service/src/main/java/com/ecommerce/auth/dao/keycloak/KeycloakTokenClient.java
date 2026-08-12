package com.ecommerce.auth.dao.keycloak;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import com.ecommerce.auth.dao.keycloak.dto.KeycloakTokenResponse;

/**
 * HTTP client for the Keycloak token endpoint — this DAO's equivalent of
 * {@code JdbcTemplate} / {@code EntityManager}: pure transport, no business
 * logic.
 */
@Path("/realms")
@RegisterRestClient(configKey = "keycloak-token-api")
@RegisterProvider(KeycloakErrorResponseMapper.class)
public interface KeycloakTokenClient {

    @POST
    @Path("/{realm}/protocol/openid-connect/token")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    KeycloakTokenResponse requestToken(@PathParam("realm") String realm,
            MultivaluedMap<String, String> form);
}
