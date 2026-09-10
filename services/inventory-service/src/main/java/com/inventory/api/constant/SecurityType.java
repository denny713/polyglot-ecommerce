package com.inventory.api.constant;

import java.util.List;

/**
 * Values shared by the two classes that deal with authentication.
 * <p>
 * {@code SECURITY_SCHEME} is the OpenAPI scheme name {@code DocConfig} registers,
 * while {@code ADMIN_ROLE} and {@code PUBLIC_PATHS} are what {@code TokenFilter}
 * enforces. They live here so the filter and the documentation cannot drift apart.
 */
public class SecurityType {

    public static final String SECURITY_SCHEME = "bearerAuth";
    public static final String ADMIN_ROLE = "admin";
    public static final List<String> PUBLIC_PATHS = List.of("/v3/api-docs", "/swagger-ui", "/error");
}
