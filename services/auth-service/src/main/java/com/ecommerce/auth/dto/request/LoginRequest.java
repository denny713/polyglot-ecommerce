package com.ecommerce.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body request {@code POST /api/auth/login}.
 *
 * <p>DTO ini adalah kontrak HTTP, terpisah dari model domain
 * {@code UserCredentials}. Dengan begitu perubahan bentuk JSON tidak merembet
 * ke service, dan sebaliknya.
 */
public record LoginRequest(

        @NotBlank(message = "username is required")
        @Size(max = 255, message = "username must not exceed 255 characters")
        String username,

        @NotBlank(message = "password is required")
        @Size(max = 255, message = "password must not exceed 255 characters")
        String password) {

    /** Password tidak pernah ikut tercetak, termasuk saat request di-log. */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=***]";
    }
}
