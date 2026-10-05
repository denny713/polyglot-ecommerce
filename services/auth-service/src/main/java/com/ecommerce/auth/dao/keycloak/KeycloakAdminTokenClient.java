package com.ecommerce.auth.dao.keycloak;

import com.ecommerce.auth.dao.keycloak.dto.KeycloakTokenResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * HTTP client for the same Keycloak token endpoint as {@link KeycloakTokenClient},
 * but used with the {@code client_credentials} grant to obtain the service
 * account token that the Admin REST API requires.
 */
@Path("/realms")
@RegisterRestClient(configKey = "keycloak-token-api")
public interface KeycloakAdminTokenClient {

    @POST
    @Path("/{realm}/protocol/openid-connect/token")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    KeycloakTokenResponse requestToken(@PathParam("realm") String realm,
            MultivaluedMap<String, String> form);
}
