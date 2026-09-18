package com.ecommerce.auth.controller;

import com.ecommerce.auth.dto.request.RegisterRequest;
import com.ecommerce.auth.dto.request.UpdateAccountRequest;
import com.ecommerce.auth.dto.request.UpdatePasswordRequest;
import com.ecommerce.auth.dto.response.AccountResponse;
import com.ecommerce.auth.dto.response.ErrorResponse;
import com.ecommerce.auth.mapper.AccountResponseMapper;
import com.ecommerce.auth.model.AccountUpdate;
import com.ecommerce.auth.model.NewAccount;
import com.ecommerce.auth.model.PasswordChange;
import com.ecommerce.auth.security.CurrentAccount;
import com.ecommerce.auth.service.AccountService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/** Account lifecycle endpoint — the counterpart of {@link AuthController}, which only deals in tokens. */
@Path("/api/account")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Account", description = "Account management")
public class AccountController {

    private final AccountService service;
    private final AccountResponseMapper mapper;
    private final CurrentAccount account;

    @Inject
    public AccountController(AccountService service, AccountResponseMapper mapper, CurrentAccount account) {
        this.service = service;
        this.mapper = mapper;
        this.account = account;
    }

    /**
     * Registers a new account in Keycloak, gives it a generated password, and
     * mails that password to the address on the request.
     *
     * @param request the account profile
     * @return {@code 201 Created} with the stored account
     * @throws jakarta.validation.ConstraintViolationException                   if the request is invalid
     * @throws com.ecommerce.auth.exception.AccountException                     if the username or email is taken
     * @throws com.ecommerce.auth.exception.NotificationDeliveryException        if the email could not be sent
     * @throws com.ecommerce.auth.exception.IdentityProviderUnavailableException if Keycloak is unavailable
     */
    @POST
    @Path("")
    @Operation(
            operationId = "register",
            summary = "Create a new account",
            description = "Create a new account in Keycloak, the generated password is emailed to the address in the request.")
    @APIResponses({
            @APIResponse(
                    responseCode = "201",
                    description = "Created",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = AccountResponse.class))),
            @APIResponse(
                    responseCode = "400",
                    description = "Bad Request, `VALIDATION_ERROR`",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(
                    responseCode = "409",
                    description = "Conflict, `ACCOUNT_ALREADY_EXISTS`",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(
                    responseCode = "503",
                    description = "Service Unavailable, `IDENTITY_PROVIDER_UNAVAILABLE` or `NOTIFICATION_UNAVAILABLE`",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response register(@Valid RegisterRequest request) {
        return Response
                .status(Response.Status.CREATED)
                .entity(mapper.toResponse(service.doRegister(new NewAccount(
                        request.username(),
                        request.email(),
                        request.firstName(),
                        request.lastName()))))
                .build();
    }

    /**
     * Reads the account the bearer token belongs to.
     *
     * @return {@code 200 OK} with the stored account
     * @throws com.ecommerce.auth.exception.AccountException                     if the account is gone
     * @throws com.ecommerce.auth.exception.IdentityProviderUnavailableException if Keycloak is unavailable
     */
    @GET
    @Path("")
    @Authenticated
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            operationId = "getCurrentAccount",
            summary = "Detail the current account",
            description = "Get details of the account the token belongs to, read fresh from Keycloak rather than from the token.")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "OK",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = AccountResponse.class))),
            @APIResponse(
                    responseCode = "401",
                    description = "Unauthorized"),
            @APIResponse(
                    responseCode = "404",
                    description = "Not Found, `ACCOUNT_NOT_FOUND`",
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
    public Response getAccount() {
        return Response.ok(mapper.toResponse(
                service.doFindById(account.id()))).build();
    }

    /**
     * Changes the profile fields of the account the bearer token belongs to.
     *
     * @param request the fields to change; omitted fields are left as they are
     * @return an empty {@code 204} response
     * @throws jakarta.validation.ConstraintViolationException                   if the request is invalid
     * @throws com.ecommerce.auth.exception.AccountException                     if the account is gone, or
     *                                                                           there is nothing to change
     * @throws com.ecommerce.auth.exception.IdentityProviderUnavailableException if Keycloak is unavailable
     */
    @PUT
    @Path("")
    @Authenticated
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            operationId = "updateCurrentAccount",
            summary = "Update the current account",
            description = "Update the profile of the account the token belongs to, an omitted field is left as it is.")
    @APIResponses({
            @APIResponse(
                    responseCode = "204",
                    description = "No Content"),
            @APIResponse(
                    responseCode = "400",
                    description = "Bad Request, `VALIDATION_ERROR` or `INVALID_ACCOUNT_DATA`",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(
                    responseCode = "401",
                    description = "Unauthorized"),
            @APIResponse(
                    responseCode = "404",
                    description = "Not Found, `ACCOUNT_NOT_FOUND`",
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
    public Response updateCurrentAccount(@Valid UpdateAccountRequest request) {
        service.doUpdate(account.id(), new AccountUpdate(
                request.email(), request.firstName(), request.lastName()));

        return Response.noContent().build();
    }

    /**
     * Replaces the password of the account the bearer token belongs to, after
     * checking the caller knows the current one.
     *
     * @param request the old password and the one to replace it with
     * @return an empty {@code 204} response
     * @throws jakarta.validation.ConstraintViolationException                   if the request is invalid
     * @throws com.ecommerce.auth.exception.AuthenticationException              if the old password is wrong,
     *                                                                           or the account is locked
     * @throws com.ecommerce.auth.exception.AccountException                     if the account is gone, or the
     *                                                                           new password was refused
     * @throws com.ecommerce.auth.exception.IdentityProviderUnavailableException if Keycloak is unavailable
     */
    @PUT
    @Path("/password")
    @Authenticated
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            operationId = "updateCurrentAccountPassword",
            summary = "Update the current account password",
            description = "Replace the password of the account the token belongs to, the old password is checked against Keycloak first.")
    @APIResponses({
            @APIResponse(
                    responseCode = "204",
                    description = "No Content"),
            @APIResponse(
                    responseCode = "400",
                    description = "Bad Request, `VALIDATION_ERROR` or `INVALID_ACCOUNT_DATA`",
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
                    responseCode = "404",
                    description = "Not Found, `ACCOUNT_NOT_FOUND`",
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
    public Response updateCurrentAccountPassword(@Valid UpdatePasswordRequest request) {
        service.doChangePassword(account.id(),
                new PasswordChange(request.oldPassword(), request.newPassword()));

        return Response.noContent().build();
    }

    /**
     * Deletes the account the bearer token belongs to.
     *
     * @return an empty {@code 204} response
     * @throws com.ecommerce.auth.exception.AccountException                     if the account is gone
     * @throws com.ecommerce.auth.exception.IdentityProviderUnavailableException if Keycloak is unavailable
     */
    @DELETE
    @Path("")
    @Authenticated
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            operationId = "deleteCurrentAccount",
            summary = "Delete the current account",
            description = "Delete the account the token belongs to, with every session and credential behind it.")
    @APIResponses({
            @APIResponse(
                    responseCode = "204",
                    description = "No Content"),
            @APIResponse(
                    responseCode = "401",
                    description = "Unauthorized"),
            @APIResponse(
                    responseCode = "404",
                    description = "Not Found, `ACCOUNT_NOT_FOUND`",
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
    public Response deleteCurrentAccount() {
        service.doDelete(account.id());

        return Response.noContent().build();
    }
}
