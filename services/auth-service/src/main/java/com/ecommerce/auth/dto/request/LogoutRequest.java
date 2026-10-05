package com.ecommerce.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Request body for {@code POST /api/auth/logout}. */
@Schema(name = "LogoutRequest", description = "The refresh token identifying the session to end")
public record LogoutRequest(

        @NotBlank(message = "refreshToken is required")
        @Size(max = 8192, message = "refreshToken must not exceed 8192 characters")
        @Schema(
                description = "The `refreshToken` returned by `POST /api/auth/login`",
                examples = "eyJhbGciOiJIUzUxMiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICI...")
        String refreshToken) {

    /** The token is never printed, not even when the request is logged. */
    @Override
    public String toString() {
        return "LogoutRequest[refreshToken=***]";
    }
}
