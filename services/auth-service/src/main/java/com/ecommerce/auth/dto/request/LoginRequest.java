package com.ecommerce.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Request body for {@code POST /api/auth/login}. */
@Schema(name = "LoginRequest", description = "Credentials registered in Keycloak")
public record LoginRequest(

        @NotBlank(message = "username is required")
        @Size(max = 255, message = "username must not exceed 255 characters")
        @Schema(description = "Keycloak username", examples = "adminapp")
        String username,

        @NotBlank(message = "password is required")
        @Size(max = 255, message = "password must not exceed 255 characters")
        @Schema(description = "Keycloak password", examples = "P@ssw0rd")
        String password) {

    /** The password is never printed, not even when the request is logged. */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=***]";
    }
}
