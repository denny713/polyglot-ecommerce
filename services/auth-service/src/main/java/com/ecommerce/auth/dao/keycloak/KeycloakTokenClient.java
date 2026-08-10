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
 * Klien HTTP ke endpoint token Keycloak — padanan {@code JdbcTemplate} /
 * {@code EntityManager}-nya DAO ini: murni transport, tanpa logika bisnis.
 *
 * <p>
 * Setara dengan curl berikut:
 *
 * <pre>{@code
 * curl --location 'http://localhost:8080/realms/ecommerce/protocol/openid-connect/token' \
 *   --header 'Content-Type: application/x-www-form-urlencoded' \
 *   --data-urlencode 'client_id=ecommerce-app' \
 *   --data-urlencode 'grant_type=password' \
 *   --data-urlencode 'username=adminapp' \
 *   --data-urlencode 'password=P@ssw0rd'
 * }</pre>
 *
 * <p>
 * Base URL-nya diambil dari
 * {@code quarkus.rest-client.keycloak-token-api.url}.
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
