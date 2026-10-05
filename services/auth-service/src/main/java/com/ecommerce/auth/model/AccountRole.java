package com.ecommerce.auth.model;

/**
 * A realm role an account can hold.
 * @see com.ecommerce.auth.service.AccountService#doRegister
 */
public enum AccountRole {

    /** The role every registered customer gets. */
    USER("user"),

    /** Never granted by this service — see the class comment. */
    ADMIN("admin");

    private final String roleName;

    AccountRole(String roleName) {
        this.roleName = roleName;
    }

    /** The role's name as the identity provider knows it. */
    public String roleName() {
        return roleName;
    }
}
