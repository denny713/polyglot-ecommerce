package com.inventory.api.token;

import com.inventory.api.constant.ResponseMsg;
import com.inventory.api.constant.SecurityType;
import com.inventory.api.model.dto.response.Response;
import com.inventory.api.util.AccountUtil;
import com.inventory.api.util.TokenUtil;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Authenticates and authorizes every request before Spring MVC sees it.
 * <p>
 * Tokens are issued by Keycloak, so this service only verifies them: the RS256
 * signature is checked against the realm's JWKS endpoint — keys are fetched on
 * first use and cached — and {@code exp} and {@code iss} are checked by the claims
 * verifier. A wrong {@code keycloak.issuer-uri} therefore rejects every token,
 * because it must equal the {@code iss} claim exactly.
 * <p>
 * Authorization is here rather than on the controllers because the whole service
 * is administrative: the realm role {@code admin} is required once, which covers
 * every endpoint that exists now and every one added later. The roles are read
 * from {@code realm_access}, where Keycloak puts realm roles; client roles would
 * arrive under {@code resource_access} instead.
 * <p>
 * The verified {@code sub} is published through {@link AccountUtil} for the
 * duration of the request and cleared afterwards, so a pooled thread cannot carry
 * one user's identity into the next request.
 * <p>
 * Being a filter, it runs outside the reach of {@code ResponseHandler}, so it
 * writes the same {@code Response} envelope itself.
 */
@Slf4j
@Component
public class TokenFilter extends OncePerRequestFilter {

    private final JsonMapper jsonMapper;
    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

    public TokenFilter(JsonMapper jsonMapper, @Value("${keycloak.issuer-uri}") String issuerUri)
            throws MalformedURLException {
        JWKSource<SecurityContext> keys = JWKSourceBuilder
                .create(URI.create(issuerUri + "/protocol/openid-connect/certs").toURL())
                .retrying(true)
                .build();

        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys));
        processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                new JWTClaimsSet.Builder().issuer(issuerUri).build(),
                Set.of("sub", "exp")));

        this.jsonMapper = jsonMapper;
        this.jwtProcessor = processor;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI().substring(request.getContextPath().length());
        return SecurityType.PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = TokenUtil.getToken(request);
        if (token == null) {
            log.error("Access token not found");
            sendErrorMessage(response, HttpStatus.UNAUTHORIZED, ResponseMsg.UNAUTHORIZED,
                    "No access token found, please login first");

            return;
        }

        UUID userLogin;
        try {
            JWTClaimsSet claims = jwtProcessor.process(token, null);
            if (!isAdmin(claims)) {
                log.error("Access token without the '{}' role: sub={}", SecurityType.ADMIN_ROLE, claims.getSubject());
                sendErrorMessage(response, HttpStatus.FORBIDDEN, ResponseMsg.FORBIDDEN,
                        "You don't have permission to access this resource");

                return;
            }

            userLogin = UUID.fromString(claims.getSubject());
        } catch (ParseException | IllegalArgumentException e) {
            log.error("Malformed access token: {}", e.getMessage());
            sendErrorMessage(response, HttpStatus.BAD_REQUEST, ResponseMsg.BAD_REQUEST, "Invalid token format");

            return;
        } catch (BadJOSEException e) {
            log.error("Rejected access token: {}", e.getMessage());
            sendErrorMessage(response, HttpStatus.UNAUTHORIZED, ResponseMsg.UNAUTHORIZED,
                    "Access token is invalid or expired, please login again");

            return;
        } catch (JOSEException e) {
            log.error("Unable to verify access token", e);
            sendErrorMessage(response, HttpStatus.INTERNAL_SERVER_ERROR, ResponseMsg.INTERNAL_SERVER_ERROR,
                    "Error occurred while processing token");

            return;
        }

        try {
            AccountUtil.setUserLogin(userLogin);
            filterChain.doFilter(request, response);
        } finally {
            AccountUtil.clearUserLogin();
        }
    }

    private static boolean isAdmin(JWTClaimsSet claims) throws ParseException {
        Map<String, Object> realmAccess = claims.getJSONObjectClaim("realm_access");
        if (realmAccess == null) {
            return false;
        }

        return realmAccess.get("roles") instanceof List<?> roles && roles.contains(SecurityType.ADMIN_ROLE);
    }

    private void sendErrorMessage(HttpServletResponse response, HttpStatus errStatus, String status, String message)
            throws IOException {
        Map<String, Object> err = new HashMap<>();
        err.put("timestamp", LocalDateTime.now());
        err.put("status", errStatus.value());
        err.put("error", message);

        Response error = new Response(errStatus.value(), status, err);

        response.setStatus(errStatus.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(jsonMapper.writeValueAsString(error));
    }
}
