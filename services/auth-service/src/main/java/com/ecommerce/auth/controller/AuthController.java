package com.ecommerce.auth.controller;

import com.ecommerce.auth.dto.request.LoginRequest;
import com.ecommerce.auth.dto.request.LogoutRequest;
import com.ecommerce.auth.mapper.LoginResponseMapper;
import com.ecommerce.auth.model.RefreshToken;
import com.ecommerce.auth.model.UserCredentials;
import com.ecommerce.auth.service.AuthenticationService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Authentication endpoint — the equivalent of {@code @RestController} in Spring Boot.
 *
 * <p>
 * It has only three jobs: accept the request, translate the DTO into the domain
 * model, and wrap the result into an HTTP response. There is no business logic
 * here, and no {@code try/catch} blocks — failures are handled by the exception
 * mappers in the {@code com.ecommerce.auth.exception.handler} package.
 */
@Path("/api/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthController {

    private final AuthenticationService authenticationService;
    private final LoginResponseMapper loginResponseMapper;

    @Inject
    public AuthController(AuthenticationService authenticationService,
                          LoginResponseMapper loginResponseMapper) {
        this.authenticationService = authenticationService;
        this.loginResponseMapper = loginResponseMapper;
    }

    /**
     * Logs in with a username and password registered in Keycloak.
     *
     * @param request the login request containing the username and password
     * @return the login response containing the access token and other details
     * @throws jakarta.validation.ConstraintViolationException                   if the request is invalid
     * @throws com.ecommerce.auth.exception.AuthenticationException              if the credentials are invalid
     * @throws com.ecommerce.auth.exception.IdentityProviderUnavailableException if Keycloak is unavailable
     */
    @POST
    @Path("/login")
    public Response login(@Valid LoginRequest request) {
        return Response.ok(loginResponseMapper.toResponse(authenticationService.doLogin(
                new UserCredentials(request.username(), request.password())))).build();
    }

    /**
     * Ends the session the refresh token belongs to.
     *
     * <p>
     * Answers {@code 204 No Content} — there is nothing meaningful to return, and
     * the same answer is given whether the session was live or had already
     * expired: logout is idempotent, and a distinguishable response would let a
     * caller probe whether a refresh token is still valid.
     *
     * <p>
     * The access token issued alongside the refresh token stays valid until it
     * expires, because a JWT cannot be recalled. Clients must discard both tokens
     * after calling this.
     *
     * @param request the logout request containing the refresh token
     * @return an empty {@code 204} response
     * @throws jakarta.validation.ConstraintViolationException                   if the request is invalid
     * @throws com.ecommerce.auth.exception.IdentityProviderUnavailableException if Keycloak is unavailable
     */
    @POST
    @Path("/logout")
    public Response logout(@Valid LogoutRequest request) {
        authenticationService.doLogout(new RefreshToken(request.refreshToken()));
        return Response.noContent().build();
    }
}
