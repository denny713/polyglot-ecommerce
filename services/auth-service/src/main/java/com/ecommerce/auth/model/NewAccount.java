package com.ecommerce.auth.model;

/** The profile of an account to be created, in a form that is neutral with respect to the identity provider. */
public record NewAccount(
        String username,
        String email,
        String firstName,
        String lastName) {

    public NewAccount {
        requireText(username, "username");
        requireText(email, "email");
        requireText(firstName, "firstName");
        requireText(lastName, "lastName");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
