package com.ecommerce.auth.controller;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

import com.ecommerce.auth.dao.IdentityProviderDao;
import com.ecommerce.auth.dao.SessionTerminationDao;
import com.ecommerce.auth.exception.AccountDisabledException;
import com.ecommerce.auth.exception.AccountLockedException;
import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import com.ecommerce.auth.exception.InvalidCredentialsException;
import com.ecommerce.auth.exception.InvalidRefreshTokenException;
import com.ecommerce.auth.model.AuthToken;
import com.ecommerce.auth.model.RefreshToken;
import com.ecommerce.auth.model.UserCredentials;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests the HTTP layer — routing, status codes, JSON shape and the exception mappers — without starting Keycloak. */
@QuarkusTest
class AuthControllerTest {

        @InjectMock
        IdentityProviderDao identityProviderDao;

        @InjectMock
        SessionTerminationDao sessionTerminationDao;

        // ------------------------------------------------------------------
        // POST /api/auth/login
        // ------------------------------------------------------------------

        @Test
        void shouldReturnTokenWhenCredentialsAreValid() {
                when(identityProviderDao.authenticate(any(UserCredentials.class)))
                                .thenReturn(new AuthToken("access-token", "refresh-token", "Bearer", 300, 1800,
                                                "profile email"));

                given()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("""
                                                {"username":"adminapp","password":"P@ssw0rd"}
                                                """)
                                .when().post("/api/auth/login")
                                .then()
                                .statusCode(200)
                                .body("accessToken", equalTo("access-token"))
                                .body("refreshToken", equalTo("refresh-token"))
                                .body("tokenType", equalTo("Bearer"))
                                .body("expiresIn", equalTo(300))
                                .body("refreshExpiresIn", equalTo(1800))
                                .body("scope", equalTo("profile email"));
        }

        @Test
        void shouldReturn401WhenCredentialsAreRejected() {
                when(identityProviderDao.authenticate(any(UserCredentials.class)))
                                .thenThrow(new InvalidCredentialsException("Invalid username or password"));

                given()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("""
                                                {"username":"adminapp","password":"wrong"}
                                                """)
                                .when().post("/api/auth/login")
                                .then()
                                .statusCode(401)
                                .body("error", equalTo("INVALID_CREDENTIALS"));
        }

        @Test
        void shouldReturn429WhenAccountIsLocked() {
                when(identityProviderDao.authenticate(any(UserCredentials.class)))
                                .thenThrow(new AccountLockedException("locked"));

                given()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("""
                                                {"username":"adminapp","password":"P@ssw0rd"}
                                                """)
                                .when().post("/api/auth/login")
                                .then()
                                .statusCode(429)
                                .body("error", equalTo("ACCOUNT_LOCKED"));
        }

        @Test
        void shouldReturn403WhenAccountIsDisabled() {
                when(identityProviderDao.authenticate(any(UserCredentials.class)))
                                .thenThrow(new AccountDisabledException("disabled"));

                given()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("""
                                                {"username":"adminapp","password":"P@ssw0rd"}
                                                """)
                                .when().post("/api/auth/login")
                                .then()
                                .statusCode(403)
                                .body("error", equalTo("ACCOUNT_DISABLED"));
        }

        @Test
        void shouldReturn503WhenKeycloakIsUnreachable() {
                when(identityProviderDao.authenticate(any(UserCredentials.class)))
                                .thenThrow(new IdentityProviderUnavailableException("down"));

                given()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("""
                                                {"username":"adminapp","password":"P@ssw0rd"}
                                                """)
                                .when().post("/api/auth/login")
                                .then()
                                .statusCode(503)
                                .body("error", equalTo("IDENTITY_PROVIDER_UNAVAILABLE"));
        }

        @Test
        void shouldReturn400WhenUsernameAndPasswordAreBlank() {
                given()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("""
                                                {"username":"","password":""}
                                                """)
                                .when().post("/api/auth/login")
                                .then()
                                .statusCode(400)
                                .body("error", equalTo("VALIDATION_ERROR"))
                                .body("details", hasSize(2))
                                .body("details[0].field", equalTo("password"))
                                .body("details[1].field", equalTo("username"));
        }

        // ------------------------------------------------------------------
        // POST /api/auth/logout
        // ------------------------------------------------------------------

        @Test
        void shouldReturn204WhenSessionIsEnded() {
                given()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("""
                                                {"refreshToken":"refresh-token"}
                                                """)
                                .when().post("/api/auth/logout")
                                .then()
                                .statusCode(204);

                verify(sessionTerminationDao).revoke(new RefreshToken("refresh-token"));
        }

        /**
         * Logout is idempotent: a session that has already ended is the outcome the
         * caller asked for, so the answer must be indistinguishable from the happy
         * path — otherwise the endpoint tells an attacker whether a stolen refresh
         * token is still live.
         */
        @Test
        void shouldReturn204WhenSessionHasAlreadyEnded() {
                doThrow(new InvalidRefreshTokenException("expired"))
                                .when(sessionTerminationDao).revoke(any(RefreshToken.class));

                given()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("""
                                                {"refreshToken":"already-revoked"}
                                                """)
                                .when().post("/api/auth/logout")
                                .then()
                                .statusCode(204);
        }

        @Test
        void shouldReturn503WhenKeycloakIsUnreachableOnLogout() {
                doThrow(new IdentityProviderUnavailableException("down"))
                                .when(sessionTerminationDao).revoke(any(RefreshToken.class));

                given()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("""
                                                {"refreshToken":"refresh-token"}
                                                """)
                                .when().post("/api/auth/logout")
                                .then()
                                .statusCode(503)
                                .body("error", equalTo("IDENTITY_PROVIDER_UNAVAILABLE"));
        }

        @Test
        void shouldReturn400WhenRefreshTokenIsBlank() {
                given()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("""
                                                {"refreshToken":""}
                                                """)
                                .when().post("/api/auth/logout")
                                .then()
                                .statusCode(400)
                                .body("error", equalTo("VALIDATION_ERROR"))
                                .body("details", hasSize(1))
                                .body("details[0].field", equalTo("refreshToken"))
                                .body("details[0].message", equalTo("refreshToken is required"));
        }
}