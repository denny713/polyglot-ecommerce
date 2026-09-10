package com.ecommerce.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for {@code PUT /api/account}.
 *
 * <p>
 * Every field is optional and {@code null} means "leave this one alone", so the
 * same endpoint serves a full and a partial update. What it does <em>not</em>
 * do is clear a field: a present-but-blank value is rejected rather than
 * written, because an account with an empty first name trips Keycloak's "Verify
 * Profile" action and locks the user out at their next login.
 *
 * <p>
 * Username and password are absent on purpose — see {@code AccountUpdate} for
 * why each one needs its own operation rather than a slot here.
 *
 * <p>
 * Sending all three as {@code null} is refused with {@code 400
 * INVALID_ACCOUNT_DATA}. That rule cannot live on a field annotation, since it
 * is the combination that is meaningless, so it is enforced in
 * {@code AccountServiceImpl}.
 */
@Schema(
        name = "UpdateAccountRequest",
        description = """
                Profile fields to change. Omit a field to leave it as it is; \
                at least one must be present.
                """)
public record UpdateAccountRequest(

        @Email(message = "email must be a well-formed address")
        @Size(max = 255, message = "email must not exceed 255 characters")
        @Schema(description = "New email address, unique within the realm", examples = "denny.a@mail.com")
        String email,

        @Pattern(regexp = ".*\\S.*", message = "firstName must not be blank")
        @Size(max = 255, message = "firstName must not exceed 255 characters")
        @Schema(description = "New given name", examples = "Denny")
        String firstName,

        @Pattern(regexp = ".*\\S.*", message = "lastName must not be blank")
        @Size(max = 255, message = "lastName must not exceed 255 characters")
        @Schema(description = "New family name", examples = "Afrizal")
        String lastName) {
}
