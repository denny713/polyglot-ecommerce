package com.ecommerce.auth.exception;

/**
 * Keycloak tidak bisa dihubungi, timeout, atau membalas dengan error yang bukan
 * soal kredensial (5xx, response tidak terbaca).
 *
 * <p>
 * Sengaja tidak menurun dari {@link AuthenticationException}: ini kegagalan
 * infrastruktur (HTTP 503), bukan kesalahan kredensial pengguna (HTTP 401).
 */
public class IdentityProviderUnavailableException extends RuntimeException {

    public IdentityProviderUnavailableException(String message) {
        super(message);
    }

    public IdentityProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
