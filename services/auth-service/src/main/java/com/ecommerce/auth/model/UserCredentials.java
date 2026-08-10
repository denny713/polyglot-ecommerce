package com.ecommerce.auth.model;

/**
 * Kredensial yang dipakai untuk proses login.
 *
 * <p>
 * Ini adalah model domain, bukan entity JPA: sumber kebenaran username dan
 * password ada di Keycloak (tabel {@code USER_ENTITY} / {@code CREDENTIAL} pada
 * database {@code keycloak}), dan service ini tidak pernah membaca tabel itu
 * secara langsung — lihat {@code com.mycompany.auth.dao.IdentityProviderDao}.
 *
 * <p>
 * {@link #toString()} sengaja ditimpa supaya password tidak pernah ikut
 * tercetak di log.
 */
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
