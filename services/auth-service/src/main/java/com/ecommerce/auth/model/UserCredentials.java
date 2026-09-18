package com.ecommerce.auth.model;

/** The credentials used during login. */
public record UserCredentials(String username, String password) {

    public UserCredentials {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }

        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("password must not be empty");
        }
    }

    @Override
    public String toString() {
        return "UserCredentials[username=" + username + ", password=***]";
    }
}
