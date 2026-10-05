package com.ecommerce.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Request body for {@code PUT /api/account/password}. */
@Schema(
        name = "UpdatePasswordRequest",
        description = "The current password, as proof of ownership, and the one to replace it with")
public record UpdatePasswordRequest(

        @NotBlank(message = "oldPassword is required")
        @Size(max = 255, message = "oldPassword must not exceed 255 characters")
        @Schema(
                description = "The password in use right now — for a freshly registered account, "
                        + "the one that arrived by email",
                examples = "K7mQ2x#9")
        String oldPassword,

        @NotBlank(message = "newPassword is required")
        @Size(max = 255, message = "newPassword must not exceed 255 characters")
        @Schema(
                description = "The replacement. Must satisfy the realm password policy and must "
                        + "differ from `oldPassword`",
                examples = "Secret#2026")
        String newPassword) {

    /** Neither password is ever printed, not even when the request is logged. */
    @Override
    public String toString() {
        return "UpdatePasswordRequest[oldPassword=***, newPassword=***]";
    }
}
