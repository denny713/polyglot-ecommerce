package com.inventory.api.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Extracts the bearer token from the {@code Authorization} header.
 * <p>
 * Returns null for anything unusable — header absent, a different scheme, or the
 * prefix with nothing after it — so the caller has one case to handle instead of
 * also guarding against an empty token.
 */
public class TokenUtil {

    private static final String BEARER_PREFIX = "Bearer ";

    private TokenUtil() {
        super();
    }

    public static String getToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
