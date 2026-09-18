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

/** Authentication endpoint — the equivalent of {@code @RestController} in Spring Boot. */
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
            summary = "Log in to an account",
            description = "Verify the credentials against Keycloak and return an access token and a refresh token.")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "OK",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = LoginResponse.class))),
            @APIResponse(
                    responseCode = "400",
                    description = "Bad Request, `VALIDATION_ERROR`",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(
                    responseCode = "401",
                    description = "Unauthorized, `INVALID_CREDENTIALS`",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(
                    responseCode = "403",
                    description = "Forbidden, `ACCOUNT_DISABLED`",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(
                    responseCode = "429",
                    description = "Too Many Requests, `ACCOUNT_LOCKED`",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(
                    responseCode = "503",
                    description = "Service Unavailable, `IDENTITY_PROVIDER_UNAVAILABLE`",
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
     * @param request the logout request containing the refresh token
     * @return an empty {@code 204} response
     * @throws jakarta.validation.ConstraintViolationException                   if the request is invalid
     * @throws com.ecommerce.auth.exception.IdentityProviderUnavailableException if Keycloak is unavailable
     */
    @POST
    @Path("/logout")
    @Operation(
            operationId = "logout",
            summary = "Log out of a session",
            description = "Revoke the refresh token and the Keycloak session behind it, an unknown token answers 204 too.")
    @APIResponses({
            @APIResponse(
                    responseCode = "204",
                    description = "No Content"),
            @APIResponse(
                    responseCode = "400",
                    description = "Bad Request, `VALIDATION_ERROR`",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(
                    responseCode = "503",
                    description = "Service Unavailable, `IDENTITY_PROVIDER_UNAVAILABLE`",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response logout(@Valid LogoutRequest request) {
        service.doLogout(new RefreshToken(request.refreshToken()));
        return Response.noContent().build();
    }
}
