package com.ecommerce.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/auth/login}.
 *
 * <p>This DTO is the HTTP contract, kept separate from the {@code UserCredentials}
 * domain model. That way changes to the JSON shape do not leak into the service,
 * and vice versa.
 */
public record LoginRequest(

        @NotBlank(message = "username is required")
        @Size(max = 255, message = "username must not exceed 255 characters")
        String username,

        @NotBlank(message = "password is required")
        @Size(max = 255, message = "password must not exceed 255 characters")
        String password) {

    /**
     * The password is never printed, not even when the request is logged.
     */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=***]";
    }
}
