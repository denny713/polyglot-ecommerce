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

/**
 * Account lifecycle endpoint — the counterpart of {@link AuthController}, which
 * only deals in tokens.
 *
 * <p>
 * It has the same three jobs and no more: accept the request, translate the DTO
 * into the domain model, and wrap the result into an HTTP response. There is no
 * business logic and no {@code try/catch} — failures are handled by the mappers
 * in {@code com.ecommerce.auth.handler}.
 *
 * <p>
 * <strong>Everything but register acts on {@code }.</strong> The account id
 * is taken from the {@code sub} claim of the bearer token, never from the URL.
 * That is not a convenience: an endpoint shaped
 * {@code /api/account/{accountId}} can be asked about somebody else's account,
 * so it needs a check that the id matches the token, and a check is something
 * a future endpoint can forget. With the id coming from the token there is no
 * way to phrase the wrong request in the first place, so there is nothing left
 * to enforce. It also means a client never needs to be told its own id before
 * it can use the API.
 *
 * <p>
 * <strong>Who may call what.</strong> {@code POST /register} is open, since a
 * new customer has no token yet; {@code application.properties} lists it in
 * {@code quarkus.http.auth.permission.public.paths} for exactly that reason.
 * Everything under {@code } demands a bearer token, stated twice over: the
 * {@code authenticated} policy on {@code /*} stops an anonymous request before
 * it reaches this class, and {@link Authenticated} on each method says the same
 * in code, so the requirement survives a configuration file being edited or
 * going missing.
 *
 * <p>
 * {@link SecurityRequirement} is documentation only — it puts the padlock in
 * Swagger UI and enforces nothing.
 *
 * <p>
 * As on {@link AuthController}, the OpenAPI annotations are written out by
 * hand: the status codes come from the exception mappers, not from anything
 * visible in a method signature, so an {@code @APIResponse} is the only place a
 * reader of the document can learn that a taken username answers 409.
 */
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
    @Path("/register")
    @Operation(
            operationId = "register",
            summary = "Create an account",
            description = """
                    Creates the account in Keycloak with a generated password, then emails that \
                    password to the address in the request.
                    
                    **The caller does not choose the password and never sees it.** It is eight \
                    characters with at least one upper-case letter, one lower-case letter, one \
                    digit and one special character, drawn from `SecureRandom`, and the email \
                    is the only place it ever appears. Registering therefore proves the person \
                    can read that mailbox — which is why a wrong address does not merely \
                    inconvenience them, it makes the account unusable.
                    
                    Open to unauthenticated callers: a new customer has no token yet.
                    
                    No token is returned either. Registering and logging in stay separate steps, \
                    so the one endpoint reachable without credentials cannot mint them. Call \
                    `POST /api/auth/login` next with the emailed password, then \
                    `PUT /api/account/password` to replace it.
                    
                    The three steps are one unit: if the email cannot be sent the account is \
                    removed again, rather than left behind with a password nobody knows while \
                    holding the username and address against a retry.
                    
                    There is no `Location` header. The account is only readable at \
                    `GET /api/account`, and the registrant cannot reach that until they have \
                    logged in — a URL they cannot yet follow would be worse than none.
                    """)
    @APIResponses({
            @APIResponse(
                    responseCode = "201",
                    description = "The account was created and the password emailed",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = AccountResponse.class))),
            @APIResponse(
                    responseCode = "400",
                    description = "`VALIDATION_ERROR` — a required field is blank, malformed or too long; "
                            + "the offending fields are listed in `details`",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(
                    responseCode = "409",
                    description = "`ACCOUNT_ALREADY_EXISTS` — the username or the email is taken. "
                            + "Which of the two is deliberately not disclosed",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(
                    responseCode = "503",
                    description = "`IDENTITY_PROVIDER_UNAVAILABLE` — Keycloak is down or not responding. "
                            + "`NOTIFICATION_UNAVAILABLE` — Keycloak was fine but the password email "
                            + "could not be sent; the account was removed again, so the same request "
                            + "can simply be retried",
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
            summary = "Read the account the token belongs to",
            description = """
                    Returns the profile Keycloak holds for the caller, read fresh from the \
                    identity provider rather than from the token — so a change made through \
                    `PUT /api/account` is visible here immediately, while the token still \
                    carries the values it was minted with.
                    
                    The account is identified by the `sub` claim; there is no way to ask about \
                    anybody else's.
                    """)
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "The account as Keycloak stores it",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = AccountResponse.class))),
            @APIResponse(
                    responseCode = "401",
                    description = "No bearer token, or it is expired or invalid"),
            @APIResponse(
                    responseCode = "404",
                    description = "`ACCOUNT_NOT_FOUND` — the account has been deleted since the "
                            + "token was issued; a JWT outlives the account it names",
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
            summary = "Change the profile of the account the token belongs to",
            description = """
                    Applies the fields present in the body and leaves the rest untouched, so \
                    the same call serves a full and a partial update. A field sent as blank is \
                    rejected rather than written — an account with an empty name trips \
                    Keycloak's *Verify Profile* action and locks the user out at their next \
                    login. Sending an empty body changes nothing and answers 400.
                    
                    Username and password are not changeable here. The username is what every \
                    already-issued token carries in `preferred_username`, and a password change \
                    needs its own re-authentication step rather than riding along with a \
                    profile edit — see `PUT /api/account/password`.
                    
                    Answers 204: the caller already has the values it just sent, so echoing them \
                    back would cost a round trip and tell it nothing new. Read them back with \
                    `GET /api/account` if you want Keycloak's version.
                    """)
    @APIResponses({
            @APIResponse(
                    responseCode = "204",
                    description = "The account was updated"),
            @APIResponse(
                    responseCode = "400",
                    description = "`VALIDATION_ERROR` — a field is malformed or too long. "
                            + "`INVALID_ACCOUNT_DATA` — no field was provided, or Keycloak rejected the data",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(
                    responseCode = "401",
                    description = "No bearer token, or it is expired or invalid"),
            @APIResponse(
                    responseCode = "404",
                    description = "`ACCOUNT_NOT_FOUND` — the account has been deleted since the "
                            + "token was issued",
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
            summary = "Replace the password of the account the token belongs to",
            description = """
                    Checks `oldPassword` against Keycloak and, if it matches, stores \
                    `newPassword` in its place.
                    
                    Both a bearer token **and** the old password are required. The token says \
                    which account is being changed — always the caller's own — and the old \
                    password says you are the person who owns it. A token can be stolen, and a \
                    stolen token that could also change the password would be a permanently \
                    stolen account.
                    
                    The check is a real login attempt, so it counts towards the realm's brute \
                    force detection: this endpoint cannot be used as an offline password \
                    oracle, and enough wrong guesses lock the account and answer 429 exactly as \
                    they would at `POST /api/auth/login`. The session that attempt opens is \
                    ended immediately and is never returned to the caller.
                    
                    `newPassword` is checked against the realm password policy by Keycloak, not \
                    by this service, so a weak one comes back as `400 INVALID_ACCOUNT_DATA` \
                    with Keycloak's own wording rather than a guess at it.
                    
                    Tokens already issued for the account keep working until they expire — a \
                    JWT cannot be recalled — so changing a password does not, on its own, end \
                    sessions elsewhere. Call `POST /api/auth/logout` with each refresh token to \
                    do that.
                    """)
    @APIResponses({
            @APIResponse(
                    responseCode = "204",
                    description = "The password was replaced"),
            @APIResponse(
                    responseCode = "400",
                    description = "`VALIDATION_ERROR` — a password is blank or too long. "
                            + "`INVALID_ACCOUNT_DATA` — the new password equals the old one, "
                            + "or the realm password policy refused it",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(
                    responseCode = "401",
                    description = "No bearer token, or it is expired or invalid. "
                            + "`INVALID_CREDENTIALS` — the token was fine but `oldPassword` is wrong",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(
                    responseCode = "403",
                    description = "`ACCOUNT_DISABLED` — the account exists but cannot log in",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(
                    responseCode = "404",
                    description = "`ACCOUNT_NOT_FOUND` — the account has been deleted since the "
                            + "token was issued",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(
                    responseCode = "429",
                    description = "`ACCOUNT_LOCKED` — too many wrong old passwords; the realm's "
                            + "brute force detection has locked the account temporarily",
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
            summary = "Delete the account the token belongs to",
            description = """
                    Removes the account and every session and credential behind it. The account \
                    is the caller's own; there is no way to name another.
                    
                    Not idempotent, unlike `POST /api/auth/logout`: a second call with a token \
                    whose account is already gone answers 404. Nothing is hidden by that, since \
                    the caller had to hold a token for the account to get here at all.
                    
                    Access tokens already issued stay valid until they expire — a JWT cannot be \
                    recalled — so a deleted account may still be seen making calls for up to one \
                    token lifetime.
                    """)
    @APIResponses({
            @APIResponse(
                    responseCode = "204",
                    description = "The account was deleted"),
            @APIResponse(
                    responseCode = "401",
                    description = "No bearer token, or it is expired or invalid"),
            @APIResponse(
                    responseCode = "404",
                    description = "`ACCOUNT_NOT_FOUND` — the account has already been deleted",
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
    public Response deleteCurrentAccount() {
        service.doDelete(account.id());

        return Response.noContent().build();
    }
}
