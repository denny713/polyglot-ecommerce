package com.ecommerce.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for {@code PUT /api/account/password}.
 *
 * <p>
 * The account id is not in this body, and not in the URL either: it is the
 * {@code sub} claim of the bearer token. There is no way to name another
 * account, so there is nothing to check.
 *
 * <p>
 * {@code oldPassword} is required even though the caller already had to present
 * a valid bearer token to reach this endpoint. A token can be stolen, and a
 * stolen token that can also change the password is a permanently stolen
 * account. Demanding the old password keeps a leaked token a temporary
 * problem.
 *
 * <p>
 * There is deliberately no complexity rule on {@code newPassword}. That policy
 * belongs to the realm ({@code length(8) and upperCase(1) and lowerCase(1) and
 * specialChars(1)} on {@code ecommerce}) and Keycloak applies it here; repeating
 * it in this DTO would create a second place to change it, and the two would
 * drift. A value the realm refuses comes back as {@code 400
 * INVALID_ACCOUNT_DATA} carrying Keycloak's own explanation.
 */
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

    /**
     * Neither password is ever printed, not even when the request is logged.
     */
    @Override
    public String toString() {
        return "UpdatePasswordRequest[oldPassword=***, newPassword=***]";
    }
}
