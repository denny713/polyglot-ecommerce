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
 *
 * <p>
 * It is a separate interface for one reason: {@link KeycloakTokenClient}
 * registers {@link KeycloakErrorResponseMapper}, which reads a 400 or 401 as
 * "the user's password was wrong". Here the only credentials in play are
 * <em>this service's own</em>, so a rejection is a deployment problem and must
 * never come back to a caller as 401. No provider is registered at all: the
 * rest client's default {@code WebApplicationException} is translated by
 * {@link KeycloakAdminTokenProvider}, which is the only class that calls this.
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
