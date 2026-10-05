package com.ecommerce.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Request body for {@code POST /api/account/register}. */
@Schema(
        name = "RegisterRequest",
        description = """
                The profile of an account to create in Keycloak. No password: one is \
                generated and emailed to `email`.
                """)
public record RegisterRequest(

        @NotBlank(message = "username is required")
        @Size(max = 255, message = "username must not exceed 255 characters")
        // The `*` rather than `+` is deliberate: an empty username is @NotBlank's
        // to report, and matching it here too would list the same field twice in
        // `details` with two different reasons for the same mistake.
        @Pattern(
                regexp = "^[A-Za-z0-9._-]*$",
                message = "username may only contain letters, digits, dots, underscores and hyphens")
        @Schema(description = "Login name, unique within the realm", examples = "denny.afrizal")
        String username,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a well-formed address")
        @Size(max = 255, message = "email must not exceed 255 characters")
        @Schema(
                description = "Email address, unique within the realm. The generated password is "
                        + "sent here, so the account is unusable if it is wrong",
                examples = "denny.afrizal@mail.com")
        String email,

        @NotBlank(message = "firstName is required")
        @Size(max = 255, message = "firstName must not exceed 255 characters")
        @Schema(description = "Given name", examples = "Denny")
        String firstName,

        @NotBlank(message = "lastName is required")
        @Size(max = 255, message = "lastName must not exceed 255 characters")
        @Schema(description = "Family name", examples = "Afrizal")
        String lastName) {
}
