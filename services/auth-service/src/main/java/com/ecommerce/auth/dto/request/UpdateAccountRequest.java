package com.ecommerce.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Request body for {@code PUT /api/account}. */
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
