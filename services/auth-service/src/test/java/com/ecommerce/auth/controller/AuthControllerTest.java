package com.ecommerce.auth.controller;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

import com.ecommerce.auth.dao.IdentityProviderDao;
import com.ecommerce.auth.exception.AccountLockedException;
import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import com.ecommerce.auth.exception.InvalidCredentialsException;
import com.ecommerce.auth.model.AuthToken;
import com.ecommerce.auth.model.UserCredentials;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests the login endpoint without starting Keycloak.
 *
 * <p>
 * This is possible precisely because the controller depends on an interface: only
 * {@link IdentityProviderDao} needs to be mocked, everything else runs as-is.
 */
@QuarkusTest
class AuthControllerTest {

        @InjectMock
        IdentityProviderDao identityProviderDao;

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
}
