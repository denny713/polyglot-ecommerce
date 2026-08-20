package com.ecommerce.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for {@code POST /api/auth/logout}.
 *
 * <p>The refresh token is what identifies the session to end — not the access
 * token. Keycloak keys its SSO session off the refresh token, and the access
 * token is a self-contained JWT that cannot be withdrawn once issued.
 *
 * <p>Like {@code LoginRequest}, this DTO is the HTTP contract and is kept
 * separate from the {@code RefreshToken} domain model.
 */
@Schema(name = "LogoutRequest", description = "The refresh token identifying the session to end")
public record LogoutRequest(

        @NotBlank(message = "refreshToken is required")
        @Size(max = 8192, message = "refreshToken must not exceed 8192 characters")
        @Schema(
                description = "The `refreshToken` returned by `POST /api/auth/login`",
                examples = "eyJhbGciOiJIUzUxMiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICI...")
        String refreshToken) {

    /**
     * The token is never printed, not even when the request is logged.
     */
    @Override
    public String toString() {
        return "LogoutRequest[refreshToken=***]";
    }
}
