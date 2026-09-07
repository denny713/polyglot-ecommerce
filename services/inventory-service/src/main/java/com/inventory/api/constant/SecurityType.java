package com.inventory.api.constant;

import java.util.List;

public class SecurityType {

    public static final String SECURITY_SCHEME = "bearerAuth";
    public static final String ADMIN_ROLE = "admin";
    public static final List<String> PUBLIC_PATHS = List.of("/v3/api-docs", "/swagger-ui", "/error");
}
