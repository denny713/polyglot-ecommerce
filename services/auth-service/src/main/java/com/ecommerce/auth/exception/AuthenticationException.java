package com.ecommerce.auth.exception;

import java.util.Objects;

/**
 * Induk dari semua kegagalan autentikasi yang <em>diharapkan</em> — yaitu yang
 * disebabkan oleh input pengguna, bukan oleh error teknis.
 *
 * <p>
 * Semua turunannya bisa diperlakukan seragam oleh satu exception mapper
 * (Liskov Substitution Principle): mapper hanya perlu membaca
 * {@link #errorCode()}, tidak perlu tahu kelas konkretnya.
 */
public abstract class AuthenticationException extends RuntimeException {

    private final AuthErrorCode errorCode;

    protected AuthenticationException(AuthErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    protected AuthenticationException(AuthErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public AuthErrorCode errorCode() {
        return errorCode;
    }
}
