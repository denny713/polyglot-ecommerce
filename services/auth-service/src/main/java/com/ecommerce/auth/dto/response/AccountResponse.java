package com.ecommerce.auth.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * An account as this API represents it — returned by
 * {@code POST /api/account/register} and by {@code GET /api/account}.
 *
 * <p>
 * One record for both on purpose. The two answer the same question, "what does
 * this account look like now?", and a second identical type would show up in
 * the OpenAPI document as a second identical schema for every client generator
 * to reproduce.
 *
 * <p>
 * No password field, in either direction. Registration generates one and mails
 * it, and Keycloak stores only a hash, so there is nothing here to return even
 * if it were wise to.
 *
 * @param id        the identifier Keycloak assigned; also the {@code sub} claim
 *                  of every token issued for this account
 * @param username  the username as Keycloak stored it
 * @param email     the email as Keycloak stored it
 * @param firstName given name
 * @param lastName  family name
 * @param enabled   whether the account may log in
 */
@Schema(name = "AccountResponse", description = "The account as Keycloak stored it")
public record AccountResponse(

        @Schema(
                description = "Keycloak's id for the account — the `sub` claim of its tokens",
                examples = "8f1a5c2e-6b3d-4f7a-9e21-0c4d8b5a7f36")
        String id,

        @Schema(
                description = "The username as stored; Keycloak normalizes it to lower case",
                examples = "denny.afrizal")
        String username,

        @Schema(description = "The email address as stored", examples = "denny.afrizal@mail.com")
        String email,

        @Schema(description = "Given name", examples = "Denny")
        String firstName,

        @Schema(description = "Family name", examples = "Afrizal")
        String lastName,

        @Schema(description = "Whether the account may log in", examples = "true")
        boolean enabled) {
}
