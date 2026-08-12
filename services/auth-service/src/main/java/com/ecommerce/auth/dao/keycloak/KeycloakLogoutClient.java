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
 *
 * <p>
 * It shares the {@code keycloak-token-api} config key with the token client on
 * purpose: same host, same timeouts, same OpenID Connect endpoint family, so
 * there is nothing to configure separately.
 *
 * <p>
 * It is a separate interface rather than one more method on the token client
 * because the two need different {@link org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper}s
 * — an HTTP 400 means "wrong password" on the token endpoint and "this session is
 * already gone" here.
 */
@Path("/realms")
@RegisterRestClient(configKey = "keycloak-token-api")
@RegisterProvider(KeycloakLogoutErrorResponseMapper.class)
public interface KeycloakLogoutClient {

    /**
     * Returns HTTP 204 with no body on success, hence the {@code void} signature.
     */
    @POST
    @Path("/{realm}/protocol/openid-connect/logout")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    void logout(@PathParam("realm") String realm,
            MultivaluedMap<String, String> form);
}