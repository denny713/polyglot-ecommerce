package com.ecommerce.auth.model;

/**
 * A realm role an account can hold.
 *
 * <p>
 * An enum rather than a bare {@code String} because the name is not free text:
 * it has to match a role that exists in the realm — {@code app/init/keycloak-init.sh}
 * creates exactly {@code admin} and {@code user} — and a typo would not fail at
 * compile time but at runtime, on the identity provider, after the account had
 * already been created.
 *
 * <p>
 * {@link #roleName()} is kept separate from {@link #name()} so the wire name
 * stays independent of the constant: the realm may one day call the role
 * something else without that renaming every reference in this codebase.
 *
 * <p>
 * {@link #ADMIN} is listed because the realm has it, not because this service
 * grants it. Registration is self-service and always yields {@link #USER} —
 * an endpoint that let a caller pick their own role would let anyone ask to be
 * an administrator. Promoting an account is an administrative act, done in the
 * Keycloak console.
 *
 * @see com.ecommerce.auth.service.AccountService#doRegister
 */
public enum AccountRole {

    /**
     * The role every registered customer gets.
     */
    USER("user"),

    /**
     * Never granted by this service — see the class comment.
     */
    ADMIN("admin");

    private final String roleName;

    AccountRole(String roleName) {
        this.roleName = roleName;
    }

    /**
     * The role's name as the identity provider knows it.
     */
    public String roleName() {
        return roleName;
    }
}
