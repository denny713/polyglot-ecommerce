package com.ecommerce.auth.dao.keycloak;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * HTTP client for the Keycloak end-session endpoint — the logout counterpart of
 * {@link KeycloakTokenClient}: pure transport, no business logic.
 */
@Path("/realms")
@RegisterRestClient(configKey = "keycloak-token-api")
@RegisterProvider(KeycloakLogoutErrorResponseMapper.class)
public interface KeycloakLogoutClient {

    /** Returns HTTP 204 with no body on success, hence the {@code void} signature. */
    @POST
    @Path("/{realm}/protocol/openid-connect/logout")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    void logout(@PathParam("realm") String realm,
            MultivaluedMap<String, String> form);
}