package com.ecommerce.auth.controller;

import com.ecommerce.auth.dto.request.LoginRequest;
import com.ecommerce.auth.dto.request.LogoutRequest;
import com.ecommerce.auth.dto.response.ErrorResponse;
import com.ecommerce.auth.dto.response.LoginResponse;
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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Authentication endpoint — the equivalent of {@code @RestController} in Spring Boot.
 *
 * <p>
 * It has only three jobs: accept the request, translate the DTO into the domain
 * model, and wrap the result into an HTTP response. There is no business logic
 * here, and no {@code try/catch} blocks — failures are handled by the exception
 * mappers in the {@code com.ecommerce.auth.exception.handler} package.
 *
 * <p>
 * The OpenAPI annotations describe what those mappers produce. They are written
 * out by hand rather than inferred, because the status codes come from the
 * mappers and not from anything visible in the method signature — an
 * {@code @APIResponse} is the only place a reader of the document can learn that
 * a locked account answers 429.
 */
@Path("/api/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Authentication", description = "Obtaining and discarding tokens")
public class AuthController {

    private final AuthenticationService service;
    private final LoginResponseMapper mapper;

    @Inject
    public AuthController(AuthenticationService service, LoginResponseMapper mapper) {
        this.service = service;
        this.mapper = mapper;
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
    @Operation(
            operationId = "login",
            summary = "Exchange username and password for a token pair",
            description = """
                    Verifies the credentials against Keycloak and returns an access token, \
                    a refresh token, and the lifetime of each.

                    Send the access token as `Authorization: Bearer <accessToken>` on \
                    subsequent calls, and keep the refresh token for `POST /api/auth/logout`.
                    """)
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "The credentials were accepted",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = LoginResponse.class))),
            @APIResponse(
                    responseCode = "400",
                    description = "`VALIDATION_ERROR` — a required field is blank or too long; "
                            + "the offending fields are listed in `details`",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(
                    responseCode = "401",
                    description = "`INVALID_CREDENTIALS` — wrong username or password",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(
                    responseCode = "403",
                    description = "`ACCOUNT_DISABLED` — the account exists but is disabled, "
                            + "or has pending required actions",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(
                    responseCode = "429",
                    description = "`ACCOUNT_LOCKED` — temporarily locked by Keycloak's brute force detection",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(
                    responseCode = "503",
                    description = "`IDENTITY_PROVIDER_UNAVAILABLE` — Keycloak is down or not responding",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response login(@Valid LoginRequest request) {
        return Response.ok(mapper.toResponse(service.doLogin(
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
    @Operation(
            operationId = "logout",
            summary = "End the session a refresh token belongs to",
            description = """
                    Revokes the refresh token and the Keycloak session behind it.

                    The call is idempotent: an unknown, expired or already-revoked token \
                    answers 204 just like a live one, so the response cannot be used to \
                    probe whether a token is still valid.

                    The access token issued alongside it stays valid until it expires — a \
                    JWT cannot be recalled — so clients must discard both tokens themselves.
                    """)
    @APIResponses({
            @APIResponse(
                    responseCode = "204",
                    description = "The session was ended, or had already ended"),
            @APIResponse(
                    responseCode = "400",
                    description = "`VALIDATION_ERROR` — `refreshToken` is blank or too long",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(
                    responseCode = "503",
                    description = "`IDENTITY_PROVIDER_UNAVAILABLE` — Keycloak is down or not responding",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response logout(@Valid LogoutRequest request) {
        service.doLogout(new RefreshToken(request.refreshToken()));
        return Response.noContent().build();
    }
}
