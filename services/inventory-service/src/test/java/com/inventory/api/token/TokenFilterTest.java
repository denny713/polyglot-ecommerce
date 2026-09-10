package com.inventory.api.token;

import com.inventory.api.constant.ResponseMsg;
import com.inventory.api.util.AccountUtil;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the filter against a real Keycloak-shaped JWKS endpoint.
 * <p>
 * The signature check is the whole point of this class, so mocking it away would
 * leave nothing worth testing. Instead an RSA key is generated in memory, its
 * public half is published by a throwaway HTTP server on a loopback port, and
 * tokens are signed for real — which is also the only way to prove that a token
 * signed by the wrong key, or for the wrong issuer, is actually rejected.
 */
class TokenFilterTest {

    private static final UUID SUBJECT = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private HttpServer jwksServer;
    private RSAKey signingKey;
    private RSAKey foreignKey;
    private String issuerUri;
    private TokenFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        foreignKey = new RSAKeyGenerator(2048).keyID("test-key").generate();

        byte[] jwks = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);

        jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwksServer.createContext("/realms/test/protocol/openid-connect/certs", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(jwks);
            }
        });
        jwksServer.start();

        issuerUri = "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/realms/test";
        filter = new TokenFilter(JSON, issuerUri);
    }

    @AfterEach
    void tearDown() {
        // stop() is idempotent, which matters because one test stops it early.
        jwksServer.stop(0);
        AccountUtil.clearUserLogin();
    }

    // ------------------------------------------------------------------
    // token fixtures
    // ------------------------------------------------------------------

    private String token(RSAKey key, String issuer, UUID subject, Instant expiry, List<String> roles)
            throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(subject == null ? null : subject.toString())
                .expirationTime(Date.from(expiry));

        if (roles != null) {
            claims.claim("realm_access", Map.of("roles", roles));
        }

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                claims.build());
        jwt.sign(new RSASSASigner(key));

        return jwt.serialize();
    }

    private String adminToken() throws Exception {
        return token(signingKey, issuerUri, SUBJECT, Instant.now().plusSeconds(300), List.of("admin", "user"));
    }

    private MockHttpServletRequest request(String path, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setContextPath("/api");
        request.setRequestURI("/api" + path);

        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }

        return request;
    }

    private static Map<String, Object> body(MockHttpServletResponse response) {
        try {
            return JSON.readValue(response.getContentAsString(), Map.class);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> errorOf(MockHttpServletResponse response) {
        return (Map<String, Object>) body(response).get("data");
    }

    // ------------------------------------------------------------------
    // the happy path
    // ------------------------------------------------------------------

    @Test
    void shouldLetAnAdminThrough() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger reached = new AtomicInteger();
        AtomicReference<UUID> seen = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            reached.incrementAndGet();
            seen.set(AccountUtil.getUserLogin());
        };

        filter.doFilter(request("/po/1", "Bearer " + adminToken()), response, chain);

        assertEquals(1, reached.get(), "the request must reach the controller");
        assertEquals(200, response.getStatus());
        assertEquals(SUBJECT, seen.get(), "the verified sub must be visible to the auditing columns");
    }

    @Test
    void shouldClearTheUserOnceTheRequestIsDone() throws Exception {
        filter.doFilter(request("/po/1", "Bearer " + adminToken()),
                new MockHttpServletResponse(), new MockFilterChain());

        // Leaving it set would hand this user's id to the next request on the thread.
        assertNull(AccountUtil.getUserLogin());
    }

    @Test
    void shouldClearTheUserEvenWhenTheControllerFails() throws Exception {
        FilterChain exploding = (req, res) -> {
            throw new IllegalStateException("controller blew up");
        };

        try {
            filter.doFilter(request("/po/1", "Bearer " + adminToken()), new MockHttpServletResponse(), exploding);
        } catch (IllegalStateException expected) {
            // The failure is meant to propagate; only the cleanup is under test.
        }

        assertNull(AccountUtil.getUserLogin());
    }

    // ------------------------------------------------------------------
    // authentication failures
    // ------------------------------------------------------------------

    @Test
    void shouldAnswerUnauthorizedWithoutAToken() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/po/1", null), response, chain);

        assertEquals(401, response.getStatus());
        assertEquals(ResponseMsg.UNAUTHORIZED, body(response).get("status"));
        assertEquals("No access token found, please login first", errorOf(response).get("error"));
        assertNull(chain.getRequest(), "the request must not reach the controller");
    }

    @Test
    void shouldAnswerBadRequestForAMalformedToken() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/po/1", "Bearer not-a-jwt"), response, new MockFilterChain());

        assertEquals(400, response.getStatus());
        assertEquals("Invalid token format", errorOf(response).get("error"));
    }

    @Test
    void shouldAnswerBadRequestWhenTheSubjectIsNotAUuid() throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                new JWTClaimsSet.Builder()
                        .issuer(issuerUri)
                        .subject("service-account")
                        .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                        .claim("realm_access", Map.of("roles", List.of("admin")))
                        .build());
        jwt.sign(new RSASSASigner(signingKey));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("/po/1", "Bearer " + jwt.serialize()), response, new MockFilterChain());

        assertEquals(400, response.getStatus());
        assertEquals("Invalid token format", errorOf(response).get("error"));
    }

    @Test
    void shouldRejectATokenSignedByAnotherKey() throws Exception {
        String forged = token(foreignKey, issuerUri, SUBJECT, Instant.now().plusSeconds(300), List.of("admin"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("/po/1", "Bearer " + forged), response, new MockFilterChain());

        // The key id matches, so only the signature check can catch this.
        assertEquals(401, response.getStatus());
        assertEquals("Access token is invalid or expired, please login again", errorOf(response).get("error"));
    }

    @Test
    void shouldRejectAnExpiredToken() throws Exception {
        String expired = token(signingKey, issuerUri, SUBJECT, Instant.now().minusSeconds(3600), List.of("admin"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("/po/1", "Bearer " + expired), response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    void shouldRejectATokenFromAnotherIssuer() throws Exception {
        String wrongIssuer = token(signingKey, "http://evil.example/realms/test", SUBJECT,
                Instant.now().plusSeconds(300), List.of("admin"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("/po/1", "Bearer " + wrongIssuer), response, new MockFilterChain());

        // This is why keycloak.issuer-uri has to match the iss claim exactly.
        assertEquals(401, response.getStatus());
    }

    @Test
    void shouldAnswerServerErrorWhenTheJwksEndpointIsUnreachable() throws Exception {
        String token = adminToken();

        // Keycloak down, or a firewall in the way: the key cannot be fetched, so
        // the token can be neither accepted nor proven bad. That is this service's
        // problem, not the caller's, so it must be a 500 rather than a 401.
        jwksServer.stop(0);
        TokenFilter offline = new TokenFilter(JSON, issuerUri);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        offline.doFilter(request("/po/1", "Bearer " + token), response, chain);

        assertEquals(500, response.getStatus());
        assertEquals(ResponseMsg.INTERNAL_SERVER_ERROR, body(response).get("status"));
        assertEquals("Error occurred while processing token", errorOf(response).get("error"));
        assertNull(chain.getRequest());
    }

    // ------------------------------------------------------------------
    // authorization: the admin role
    // ------------------------------------------------------------------

    @Test
    void shouldAnswerForbiddenForANonAdmin() throws Exception {
        String userOnly = token(signingKey, issuerUri, SUBJECT, Instant.now().plusSeconds(300), List.of("user"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/po/1", "Bearer " + userOnly), response, chain);

        assertEquals(403, response.getStatus());
        assertEquals(ResponseMsg.FORBIDDEN, body(response).get("status"));
        assertNull(chain.getRequest());
    }

    @Test
    void shouldAnswerForbiddenWhenTheTokenCarriesNoRolesAtAll() throws Exception {
        String noRoles = token(signingKey, issuerUri, SUBJECT, Instant.now().plusSeconds(300), null);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("/po/1", "Bearer " + noRoles), response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @Test
    void shouldAnswerForbiddenWhenRealmAccessHasAnEmptyRoleList() throws Exception {
        String noRoles = token(signingKey, issuerUri, SUBJECT, Instant.now().plusSeconds(300), List.of());

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("/po/1", "Bearer " + noRoles), response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @Test
    void shouldIgnoreARoleThatMerelyContainsAdmin() throws Exception {
        String lookalike = token(signingKey, issuerUri, SUBJECT, Instant.now().plusSeconds(300),
                List.of("administrator", "admin-readonly"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("/po/1", "Bearer " + lookalike), response, new MockFilterChain());

        // The check is an exact match, not a substring.
        assertEquals(403, response.getStatus());
    }

    // ------------------------------------------------------------------
    // the paths that are deliberately open
    // ------------------------------------------------------------------

    @Test
    void shouldSkipTheApiDocs() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request("/v3/api-docs", null), new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest(), "swagger must be readable without a token");
    }

    @Test
    void shouldSkipTheSwaggerUi() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request("/swagger-ui/index.html", null), new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
    }

    @Test
    void shouldSkipTheErrorPage() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request("/error", null), new MockHttpServletResponse(), chain);

        // Blocking this would break Spring Boot's own error rendering.
        assertNotNull(chain.getRequest());
    }

    @Test
    void shouldSkipPreflightRequests() throws Exception {
        MockHttpServletRequest preflight = new MockHttpServletRequest("OPTIONS", "/po/1");
        preflight.setContextPath("/api");
        preflight.setRequestURI("/api/po/1");

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(preflight, new MockHttpServletResponse(), chain);

        // A CORS preflight never carries Authorization, so blocking it would fail
        // the real request before it is even sent.
        assertNotNull(chain.getRequest());
    }

    @Test
    void shouldStillProtectAPathThatOnlyLooksLikeADocsPath() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/po/v3/api-docs", null), response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest());
    }

    // ------------------------------------------------------------------
    // the error body
    // ------------------------------------------------------------------

    @Test
    void shouldWriteTheSameEnvelopeAsTheRestOfTheApi() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/po/1", null), response, new MockFilterChain());

        assertEquals("application/json", response.getContentType());

        Map<String, Object> envelope = body(response);
        assertEquals(401, envelope.get("code"));
        assertNotNull(envelope.get("status"));

        Map<String, Object> error = errorOf(response);
        assertEquals(401, error.get("status"));
        assertNotNull(error.get("timestamp"), "the timestamp must survive serialization");
        assertTrue(error.containsKey("error"));
    }
}
