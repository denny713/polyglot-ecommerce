package com.inventory.api.util;

import jakarta.servlet.http.HttpServletRequest;

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
