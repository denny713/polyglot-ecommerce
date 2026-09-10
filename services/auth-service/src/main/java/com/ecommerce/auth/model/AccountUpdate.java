package com.ecommerce.auth.model;

/**
 * The profile fields an update may change.
 *
 * <p>
 * Username is absent on purpose. It is the login identifier other services
 * carry in the {@code preferred_username} claim of every token already issued,
 * and renaming it would leave those tokens pointing at a name that no longer
 * exists until they expire. Keycloak itself treats it as immutable unless the
 * realm opts in.
 *
 * <p>
 * Password is absent for a different reason: changing one is a separate
 * operation with its own rules — it needs the current password, or a
 * re-authentication — and folding it into a profile update would let a stolen
 * access token silently take the account over.
 *
 * <p>
 * A {@code null} field means "leave this one alone", which is what makes
 * {@code PUT} usable for a partial update here; the DAO omits nulls from the
 * representation it sends, and Keycloak leaves the stored value untouched.
 */
public record AccountUpdate(
        String email,
        String firstName,
        String lastName) {

    /**
     * True when every field is {@code null} — there is nothing for the identity
     * provider to do, so the service can reject the call instead of spending a
     * round trip on it.
     */
    public boolean isEmpty() {
        return email == null && firstName == null && lastName == null;
    }
}
