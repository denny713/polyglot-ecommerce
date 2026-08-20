package com.ecommerce.auth.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Success response body for {@code POST /api/auth/login}.
 *
 * @param accessToken      JWT to be sent as {@code Authorization: Bearer ...}
 * @param refreshToken     token used to renew the access token
 * @param tokenType        usually {@code Bearer}
 * @param expiresIn        access token lifetime, in seconds
 * @param refreshExpiresIn refresh token lifetime, in seconds
 * @param scope            the scope actually granted by Keycloak
 */
@Schema(name = "LoginResponse", description = "The token pair issued by Keycloak")
public record LoginResponse(

        @Schema(
                description = "JWT to be sent as `Authorization: Bearer <accessToken>`",
                examples = "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICI...")
        String accessToken,

        @Schema(
                description = "Token used to renew the access token, and to log out",
                examples = "eyJhbGciOiJIUzUxMiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICI...")
        String refreshToken,

        @Schema(description = "Authorization scheme the access token is used with", examples = "Bearer")
        String tokenType,

        @Schema(description = "Access token lifetime, in seconds", examples = "300")
        long expiresIn,

        @Schema(description = "Refresh token lifetime, in seconds", examples = "1800")
        long refreshExpiresIn,

        @Schema(description = "The scope actually granted by Keycloak", examples = "profile email")
        String scope) {
}
