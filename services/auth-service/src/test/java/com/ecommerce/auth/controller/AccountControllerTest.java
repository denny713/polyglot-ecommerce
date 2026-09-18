package com.ecommerce.auth.controller;

import com.ecommerce.auth.dao.AccountProviderDao;
import com.ecommerce.auth.dao.IdentityProviderDao;
import com.ecommerce.auth.dao.SessionTerminationDao;
import com.ecommerce.auth.exception.AccountAlreadyExistsException;
import com.ecommerce.auth.exception.AccountNotFoundException;
import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import com.ecommerce.auth.exception.InvalidAccountDataException;
import com.ecommerce.auth.exception.AccountLockedException;
import com.ecommerce.auth.exception.InvalidCredentialsException;
import com.ecommerce.auth.exception.NotificationDeliveryException;
import com.ecommerce.auth.model.Account;
import com.ecommerce.auth.model.AccountUpdate;
import com.ecommerce.auth.model.AuthToken;
import com.ecommerce.auth.model.RefreshToken;
import com.ecommerce.auth.model.NewAccount;
import com.ecommerce.auth.model.RawPassword;
import com.ecommerce.auth.model.UserCredentials;
import com.ecommerce.auth.notification.AccountNotifier;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the HTTP layer of {@link AccountController} — routing, status codes,
 * JSON shape, the exception mappers and the access rule — without starting
 * Keycloak.
 */
@QuarkusTest
class AccountControllerTest {

    private static final String OWN_ID = "8f1a5c2e-6b3d-4f7a-9e21-0c4d8b5a7f36";
    private static final String SOMEONE_ELSE = "1b2c3d4e-5f60-4718-8293-a4b5c6d7e8f9";
    private static final String USERNAME = "denny.afrizal";
    private static final String USER = "user";

    @InjectMock
    AccountProviderDao accountProviderDao;

    @InjectMock
    AccountNotifier accountNotifier;

    /**
     * Mocked so that verifying the old password does not need a live Keycloak.
     * {@code CurrentPasswordVerifier} itself is left real — the rule that a
     * wrong password must not reach the write is part of what these tests check.
     */
    @InjectMock
    IdentityProviderDao identityProviderDao;

    @InjectMock
    SessionTerminationDao sessionTerminationDao;

    // ------------------------------------------------------------------
    // POST /api/account
    // ------------------------------------------------------------------

    @Test
    void shouldReturn201WithTheStoredAccountWhenRegistrationSucceeds() {
        when(accountProviderDao.doCreate(any(NewAccount.class), any(RawPassword.class))).thenReturn(new Account(
                OWN_ID, USERNAME, "denny@mail.com", "Denny", "Afrizal", true));

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username":"denny.afrizal","email":"denny@mail.com",\
                        "firstName":"Denny","lastName":"Afrizal"}
                        """)
                .when().post("/api/account")
                .then()
                .statusCode(201)
                .body("id", equalTo(OWN_ID))
                .body("username", equalTo(USERNAME))
                .body("email", equalTo("denny@mail.com"))
                .body("firstName", equalTo("Denny"))
                .body("lastName", equalTo("Afrizal"))
                .body("enabled", equalTo(true));
    }

    /**
     * Registration has to work without a token — a new customer has none yet.
     * This is the assertion that catches someone tightening the public path list
     * in {@code .env} and locking new customers out.
     */
    @Test
    void shouldAllowRegistrationWithoutAToken() {
        when(accountProviderDao.doCreate(any(NewAccount.class), any(RawPassword.class))).thenReturn(new Account(
                OWN_ID, USERNAME, "denny@mail.com", "Denny", "Afrizal", true));

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username":"denny.afrizal","email":"denny@mail.com",\
                        "firstName":"Denny","lastName":"Afrizal"}
                        """)
                .when().post("/api/account")
                .then()
                .statusCode(201);
    }

    /**
     * The caller never learns the password, and the account is unusable until
     * someone reads the mailbox. That is the whole security property of a
     * registration endpoint open to the internet.
     */
    @Test
    void shouldMailAGeneratedPasswordRatherThanReturnIt() {
        when(accountProviderDao.doCreate(any(NewAccount.class), any(RawPassword.class))).thenReturn(new Account(
                OWN_ID, USERNAME, "denny@mail.com", "Denny", "Afrizal", true));

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username":"denny.afrizal","email":"denny@mail.com",\
                        "firstName":"Denny","lastName":"Afrizal"}
                        """)
                .when().post("/api/account")
                .then()
                .statusCode(201);

        ArgumentCaptor<RawPassword> stored = ArgumentCaptor.forClass(RawPassword.class);
        ArgumentCaptor<RawPassword> mailed = ArgumentCaptor.forClass(RawPassword.class);
        verify(accountProviderDao).doCreate(any(NewAccount.class), stored.capture());
        verify(accountNotifier).sendTemporaryPassword(any(Account.class), mailed.capture());

        assertEquals(8, stored.getValue().value().length());
        assertEquals(stored.getValue().value(), mailed.getValue().value(),
                "the account was given one password and the holder was told another");
    }

    /**
     * Left behind, it would be an account with a password nobody knows, holding
     * the username and address against the retry.
     */
    @Test
    void shouldReturn503AndRemoveTheAccountWhenThePasswordEmailFails() {
        when(accountProviderDao.doCreate(any(NewAccount.class), any(RawPassword.class))).thenReturn(new Account(
                OWN_ID, USERNAME, "denny@mail.com", "Denny", "Afrizal", true));
        doThrow(new NotificationDeliveryException("smtp down", new RuntimeException()))
                .when(accountNotifier).sendTemporaryPassword(any(), any());

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username":"denny.afrizal","email":"denny@mail.com",\
                        "firstName":"Denny","lastName":"Afrizal"}
                        """)
                .when().post("/api/account")
                .then()
                .statusCode(503)
                .body("error", equalTo("NOTIFICATION_UNAVAILABLE"));

        verify(accountProviderDao).doDelete(OWN_ID);
    }

    @Test
    void shouldNeverReturnATokenFromRegistration() {
        when(accountProviderDao.doCreate(any(NewAccount.class), any(RawPassword.class))).thenReturn(new Account(
                OWN_ID, USERNAME, "denny@mail.com", "Denny", "Afrizal", true));

        String body = given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username":"denny.afrizal","email":"denny@mail.com",\
                        "firstName":"Denny","lastName":"Afrizal"}
                        """)
                .when().post("/api/account")
                .then().statusCode(201)
                .extract().asString();

        org.junit.jupiter.api.Assertions.assertFalse(body.contains("Token"),
                "registering must not mint credentials — the client calls /api/auth/login next");
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("password"),
                "the response echoed the password back: " + body);
    }

    @Test
    void shouldReturn409WhenTheUsernameOrEmailIsTaken() {
        when(accountProviderDao.doCreate(any(NewAccount.class), any(RawPassword.class)))
                .thenThrow(new AccountAlreadyExistsException(
                        "An account with that username or email address already exists"));

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username":"denny.afrizal","email":"denny@mail.com",\
                        "firstName":"Denny","lastName":"Afrizal"}
                        """)
                .when().post("/api/account")
                .then()
                .statusCode(409)
                .body("error", equalTo("ACCOUNT_ALREADY_EXISTS"))
                .body("message", equalTo("An account with that username or email address already exists"));
    }

    @Test
    void shouldReturn503WhenKeycloakIsUnreachableOnRegister() {
        when(accountProviderDao.doCreate(any(NewAccount.class), any(RawPassword.class)))
                .thenThrow(new IdentityProviderUnavailableException("down"));

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username":"denny.afrizal","email":"denny@mail.com",\
                        "firstName":"Denny","lastName":"Afrizal"}
                        """)
                .when().post("/api/account")
                .then()
                .statusCode(503)
                .body("error", equalTo("IDENTITY_PROVIDER_UNAVAILABLE"));
    }

    @Test
    void shouldReturn400ListingEveryMissingRegistrationField() {
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username":"","email":"","firstName":"","lastName":""}
                        """)
                .when().post("/api/account")
                .then()
                .statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"))
                .body("details", hasSize(4))
                .body("details[0].field", equalTo("email"))
                .body("details[1].field", equalTo("firstName"))
                .body("details[2].field", equalTo("lastName"))
                .body("details[3].field", equalTo("username"))
                // Once, not twice: the "required" rule and the "shape" rule must not
                // both fire on the same empty value.
                .body("details[3].message", equalTo("username is required"));
    }

    @Test
    void shouldReturn400WhenTheEmailIsMalformed() {
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username":"denny.afrizal","email":"not-an-address",\
                        "firstName":"Denny","lastName":"Afrizal"}
                        """)
                .when().post("/api/account")
                .then()
                .statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"))
                .body("details[0].field", equalTo("email"))
                .body("details[0].message", equalTo("email must be a well-formed address"));
    }

    @Test
    void shouldReturn400WhenTheUsernameHasCharactersKeycloakWouldMangle() {
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username":"denny afrizal!","email":"denny@mail.com",\
                        "firstName":"Denny","lastName":"Afrizal"}
                        """)
                .when().post("/api/account")
                .then()
                .statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"))
                .body("details[0].field", equalTo("username"));
    }

    // ------------------------------------------------------------------
    // GET /api/account
    // ------------------------------------------------------------------

    @Test
    @TestSecurity(user = USERNAME, roles = USER)
    @OidcSecurity(claims = @Claim(key = "sub", value = OWN_ID))
    void shouldReturnTheAccountTheTokenBelongsTo() {
        when(accountProviderDao.doFindById(OWN_ID)).thenReturn(new Account(
                OWN_ID, USERNAME, "denny@mail.com", "Denny", "Afrizal", true));

        given()
                .when().get("/api/account")
                .then()
                .statusCode(200)
                .body("id", equalTo(OWN_ID))
                .body("username", equalTo(USERNAME))
                .body("email", equalTo("denny@mail.com"))
                .body("firstName", equalTo("Denny"))
                .body("lastName", equalTo("Afrizal"))
                .body("enabled", equalTo(true));
    }

    /**
     * Read from Keycloak, not from the token: a profile change made through
     * {@code PUT /api/account} has to be visible immediately, while the
     * bearer token still carries the values it was minted with.
     */
    @Test
    @TestSecurity(user = USERNAME, roles = USER)
    @OidcSecurity(claims = @Claim(key = "sub", value = OWN_ID))
    void shouldReadTheProfileFromKeycloakRatherThanFromTheToken() {
        when(accountProviderDao.doFindById(OWN_ID)).thenReturn(new Account(
                OWN_ID, USERNAME, "baru@mail.com", "Denny Baru", "Afrizal", true));

        given()
                .when().get("/api/account")
                .then()
                .statusCode(200)
                .body("email", equalTo("baru@mail.com"))
                .body("firstName", equalTo("Denny Baru"));

        verify(accountProviderDao).doFindById(OWN_ID);
    }

    /**
     * The assertion that stands in for every deleted "somebody else's account"
     * test: nothing in the request names an account, so the only place the id
     * can have come from is the token.
     */
    @Test
    @TestSecurity(user = "someone.else", roles = "user")
    @OidcSecurity(claims = @Claim(key = "sub", value = SOMEONE_ELSE))
    void shouldActOnWhicheverAccountTheTokenNames() {
        when(accountProviderDao.doFindById(SOMEONE_ELSE)).thenReturn(new Account(
                SOMEONE_ELSE, "someone.else", "lain@mail.com", "Someone", "Else", true));

        given()
                .when().get("/api/account")
                .then()
                .statusCode(200)
                .body("id", equalTo(SOMEONE_ELSE));

        verify(accountProviderDao).doFindById(SOMEONE_ELSE);
        verify(accountProviderDao, never()).doFindById(OWN_ID);
    }

    @Test
    void shouldReturn401WhenReadingTheAccountWithoutAToken() {
        given()
                .when().get("/api/account")
                .then()
                .statusCode(401);

        verify(accountProviderDao, never()).doFindById(any());
    }

    /** A JWT outlives the account it names, so a valid token can point at something that is no longer there. */
    @Test
    @TestSecurity(user = USERNAME, roles = USER)
    @OidcSecurity(claims = @Claim(key = "sub", value = OWN_ID))
    void shouldReturn404WhenTheAccountTheTokenNamesIsGone() {
        when(accountProviderDao.doFindById(OWN_ID))
                .thenThrow(new AccountNotFoundException("No account exists with that id"));

        given()
                .when().get("/api/account")
                .then()
                .statusCode(404)
                .body("error", equalTo("ACCOUNT_NOT_FOUND"));
    }

    @Test
    @TestSecurity(user = USERNAME, roles = USER)
    @OidcSecurity(claims = @Claim(key = "sub", value = OWN_ID))
    void shouldReturn503WhenKeycloakIsUnreachableOnRead() {
        when(accountProviderDao.doFindById(OWN_ID))
                .thenThrow(new IdentityProviderUnavailableException("down"));

        given()
                .when().get("/api/account")
                .then()
                .statusCode(503)
                .body("error", equalTo("IDENTITY_PROVIDER_UNAVAILABLE"));
    }

    // ------------------------------------------------------------------
    // PUT /api/account
    // ------------------------------------------------------------------

    @Test
    @TestSecurity(user = USERNAME, roles = USER)
    @OidcSecurity(claims = @Claim(key = "sub", value = OWN_ID))
    void shouldReturn204WhenTheProfileIsUpdated() {
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"email":"baru@mail.com"}
                        """)
                .when().put("/api/account")
                .then()
                .statusCode(204);

        verify(accountProviderDao).doUpdate(OWN_ID, new AccountUpdate("baru@mail.com", null, null));
    }

    @Test
    void shouldReturn401WhenUpdatingWithoutAToken() {
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"email":"baru@mail.com"}
                        """)
                .when().put("/api/account")
                .then()
                .statusCode(401);

        verify(accountProviderDao, never()).doUpdate(any(), any());
    }

    @Test
    @TestSecurity(user = USERNAME, roles = USER)
    @OidcSecurity(claims = @Claim(key = "sub", value = OWN_ID))
    void shouldReturn400WhenAnUpdateChangesNothing() {
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .when().put("/api/account")
                .then()
                .statusCode(400)
                .body("error", equalTo("INVALID_ACCOUNT_DATA"))
                .body("message", equalTo("At least one field must be provided to update"));
    }

    /**
     * A blank name is not "clear this field": Keycloak's *Verify Profile* action
     * then stops the user at a setup screen on their next login.
     */
    @Test
    @TestSecurity(user = USERNAME, roles = USER)
    @OidcSecurity(claims = @Claim(key = "sub", value = OWN_ID))
    void shouldReturn400WhenAnUpdateTriesToBlankOutAName() {
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"firstName":"   "}
                        """)
                .when().put("/api/account")
                .then()
                .statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"))
                .body("details[0].field", equalTo("firstName"))
                .body("details[0].message", equalTo("firstName must not be blank"));
    }

    @Test
    @TestSecurity(user = USERNAME, roles = USER)
    @OidcSecurity(claims = @Claim(key = "sub", value = OWN_ID))
    void shouldReturn404WhenUpdatingAnAccountThatIsGone() {
        doThrow(new AccountNotFoundException("No account exists with that id"))
                .when(accountProviderDao).doUpdate(any(), any());

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"email":"baru@mail.com"}
                        """)
                .when().put("/api/account")
                .then()
                .statusCode(404)
                .body("error", equalTo("ACCOUNT_NOT_FOUND"));
    }

    @Test
    @TestSecurity(user = USERNAME, roles = USER)
    @OidcSecurity(claims = @Claim(key = "sub", value = OWN_ID))
    void shouldReturn503WhenKeycloakIsUnreachableOnUpdate() {
        doThrow(new IdentityProviderUnavailableException("down"))
                .when(accountProviderDao).doUpdate(any(), any());

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"email":"baru@mail.com"}
                        """)
                .when().put("/api/account")
                .then()
                .statusCode(503)
                .body("error", equalTo("IDENTITY_PROVIDER_UNAVAILABLE"));
    }

    // ------------------------------------------------------------------
    // PUT /api/account/password
    // ------------------------------------------------------------------

    @Test
    @TestSecurity(user = USERNAME, roles = USER)
    @OidcSecurity(claims = @Claim(key = "sub", value = OWN_ID))
    void shouldReturn204WhenTheOldPasswordIsCorrect() {
        givenTheAccountExists();
        givenTheOldPasswordIsAccepted();

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"oldPassword":"K7mQ2x#9","newPassword":"Secret#2026"}
                        """)
                .when().put("/api/account/password")
                .then()
                .statusCode(204);

        verify(accountProviderDao).doChangePassword(OWN_ID, new RawPassword("Secret#2026"));
    }

    /**
     * The check is a real login attempt, which is what keeps the realm's brute
     * force detection in the loop — and it opens a session that has to be tidied
     * away rather than handed to the caller.
     */
    @Test
    @TestSecurity(user = USERNAME, roles = USER)
    @OidcSecurity(claims = @Claim(key = "sub", value = OWN_ID))
    void shouldEndTheSessionOpenedToVerifyTheOldPassword() {
        givenTheAccountExists();
        givenTheOldPasswordIsAccepted();

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"oldPassword":"K7mQ2x#9","newPassword":"Secret#2026"}
                        """)
                .when().put("/api/account/password")
                .then()
                .statusCode(204);

        verify(sessionTerminationDao).revoke(new RefreshToken("refresh-token"));
    }

    /** A valid bearer token is not enough. If it were, a stolen token would be a permanently stolen account. */
    @Test
    @TestSecurity(user = USERNAME, roles = USER)
    @OidcSecurity(claims = @Claim(key = "sub", value = OWN_ID))
    void shouldReturn401WhenTheOldPasswordIsWrongEvenWithAValidToken() {
        givenTheAccountExists();
        when(identityProviderDao.authenticate(any(UserCredentials.class)))
                .thenThrow(new InvalidCredentialsException("Invalid username or password"));

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"oldPassword":"salah","newPassword":"Secret#2026"}
                        """)
                .when().put("/api/account/password")
                .then()
                .statusCode(401)
                .body("error", equalTo("INVALID_CREDENTIALS"));

        verify(accountProviderDao, never()).doChangePassword(any(), any());
    }

    @Test
    @TestSecurity(user = USERNAME, roles = USER)
    @OidcSecurity(claims = @Claim(key = "sub", value = OWN_ID))
    void shouldReturn429WhenTooManyWrongOldPasswordsLockedTheAccount() {
        givenTheAccountExists();
        when(identityProviderDao.authenticate(any(UserCredentials.class)))
                .thenThrow(new AccountLockedException("locked"));

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"oldPassword":"salah","newPassword":"Secret#2026"}
                        """)
                .when().put("/api/account/password")
                .then()
                .statusCode(429)
                .body("error", equalTo("ACCOUNT_LOCKED"));
    }

    @Test
    void shouldReturn401WhenChangingAPasswordWithoutAToken() {
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"oldPassword":"K7mQ2x#9","newPassword":"Secret#2026"}
                        """)
                .when().put("/api/account/password")
                .then()
                .statusCode(401);

        verify(accountProviderDao, never()).doChangePassword(any(), any());
    }

    @Test
    @TestSecurity(user = USERNAME, roles = USER)
    @OidcSecurity(claims = @Claim(key = "sub", value = OWN_ID))
    void shouldReturn400WhenTheNewPasswordEqualsTheOldOne() {
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"oldPassword":"K7mQ2x#9","newPassword":"K7mQ2x#9"}
                        """)
                .when().put("/api/account/password")
                .then()
                .statusCode(400)
                .body("error", equalTo("INVALID_ACCOUNT_DATA"))
                .body("message", equalTo("The new password must be different from the current one"));
    }

    @Test
    @TestSecurity(user = USERNAME, roles = USER)
    @OidcSecurity(claims = @Claim(key = "sub", value = OWN_ID))
    void shouldReturn400ListingBothMissingPasswords() {
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"oldPassword":"","newPassword":""}
                        """)
                .when().put("/api/account/password")
                .then()
                .statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"))
                .body("details", hasSize(2))
                .body("details[0].field", equalTo("newPassword"))
                .body("details[1].field", equalTo("oldPassword"));
    }

    /** The realm owns the policy, so its wording is passed through rather than guessed at here. */
    @Test
    @TestSecurity(user = USERNAME, roles = USER)
    @OidcSecurity(claims = @Claim(key = "sub", value = OWN_ID))
    void shouldReturn400WhenTheRealmPolicyRefusesTheNewPassword() {
        givenTheAccountExists();
        givenTheOldPasswordIsAccepted();
        doThrow(new InvalidAccountDataException("Password policy not met: specialChars"))
                .when(accountProviderDao).doChangePassword(any(), any());

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"oldPassword":"K7mQ2x#9","newPassword":"lemah"}
                        """)
                .when().put("/api/account/password")
                .then()
                .statusCode(400)
                .body("error", equalTo("INVALID_ACCOUNT_DATA"))
                .body("message", containsString("Password policy"));
    }

    @Test
    @TestSecurity(user = USERNAME, roles = USER)
    @OidcSecurity(claims = @Claim(key = "sub", value = OWN_ID))
    void shouldReturn404WhenChangingThePasswordOfAnAccountThatIsGone() {
        when(accountProviderDao.doFindById(OWN_ID))
                .thenThrow(new AccountNotFoundException("No account exists with that id"));

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"oldPassword":"K7mQ2x#9","newPassword":"Secret#2026"}
                        """)
                .when().put("/api/account/password")
                .then()
                .statusCode(404)
                .body("error", equalTo("ACCOUNT_NOT_FOUND"));
    }

    @Test
    @TestSecurity(user = USERNAME, roles = USER)
    @OidcSecurity(claims = @Claim(key = "sub", value = OWN_ID))
    void shouldReturn503WhenKeycloakIsUnreachableOnPasswordChange() {
        givenTheAccountExists();
        givenTheOldPasswordIsAccepted();
        doThrow(new IdentityProviderUnavailableException("down"))
                .when(accountProviderDao).doChangePassword(any(), any());

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"oldPassword":"K7mQ2x#9","newPassword":"Secret#2026"}
                        """)
                .when().put("/api/account/password")
                .then()
                .statusCode(503)
                .body("error", equalTo("IDENTITY_PROVIDER_UNAVAILABLE"));
    }

    private void givenTheAccountExists() {
        when(accountProviderDao.doFindById(OWN_ID)).thenReturn(new Account(
                OWN_ID, USERNAME, "denny@mail.com", "Denny", "Afrizal", true));
    }

    private void givenTheOldPasswordIsAccepted() {
        when(identityProviderDao.authenticate(any(UserCredentials.class)))
                .thenReturn(new AuthToken("access-token", "refresh-token", "Bearer", 300, 1800, "profile"));
    }

    // ------------------------------------------------------------------
    // DELETE /api/account
    // ------------------------------------------------------------------

    @Test
    @TestSecurity(user = USERNAME, roles = USER)
    @OidcSecurity(claims = @Claim(key = "sub", value = OWN_ID))
    void shouldReturn204WhenTheAccountIsDeleted() {
        given()
                .when().delete("/api/account")
                .then()
                .statusCode(204);

        verify(accountProviderDao).doDelete(OWN_ID);
    }

    @Test
    void shouldReturn401WhenDeletingWithoutAToken() {
        given()
                .when().delete("/api/account")
                .then()
                .statusCode(401);

        verify(accountProviderDao, never()).doDelete(any());
    }

    /**
     * Deliberately not idempotent, unlike {@code POST /api/auth/logout}: the
     * caller had to hold a token for the account to get here at all, so a 404
     * tells it nothing it did not already know.
     */
    @Test
    @TestSecurity(user = USERNAME, roles = USER)
    @OidcSecurity(claims = @Claim(key = "sub", value = OWN_ID))
    void shouldReturn404WhenDeletingAnAccountThatIsAlreadyGone() {
        doThrow(new AccountNotFoundException("No account exists with that id"))
                .when(accountProviderDao).doDelete(any());

        given()
                .when().delete("/api/account")
                .then()
                .statusCode(404)
                .body("error", equalTo("ACCOUNT_NOT_FOUND"));
    }

    @Test
    @TestSecurity(user = USERNAME, roles = USER)
    @OidcSecurity(claims = @Claim(key = "sub", value = OWN_ID))
    void shouldReturn503WhenKeycloakIsUnreachableOnDelete() {
        doThrow(new IdentityProviderUnavailableException("down"))
                .when(accountProviderDao).doDelete(any());

        given()
                .when().delete("/api/account")
                .then()
                .statusCode(503)
                .body("error", equalTo("IDENTITY_PROVIDER_UNAVAILABLE"));
    }
}
