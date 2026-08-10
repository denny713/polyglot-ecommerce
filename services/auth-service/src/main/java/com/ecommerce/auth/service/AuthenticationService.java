package com.ecommerce.auth.service;

import com.ecommerce.auth.exception.AuthenticationException;
import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import com.ecommerce.auth.model.AuthToken;
import com.ecommerce.auth.model.UserCredentials;

/**
 * Kontrak business logic autentikasi — inilah yang dipakai controller.
 *
 * <p>
 * Bekerja dengan model domain ({@link UserCredentials}, {@link AuthToken}),
 * bukan dengan DTO HTTP, supaya logikanya bisa dipakai ulang dari pemicu lain
 * (gRPC, message consumer, scheduled job) tanpa membawa-bawa JAX-RS.
 *
 * @see com.ecommerce.auth.service.impl.DefaultAuthenticationService
 */
public interface AuthenticationService {

    /**
     * Melakukan login dengan username dan password.
     *
     * @param credentials kredensial pengguna
     * @return token yang diterbitkan identity provider
     * @throws AuthenticationException              kredensial ditolak
     * @throws IdentityProviderUnavailableException identity provider tidak tersedia
     */
    AuthToken doLogin(UserCredentials credentials);
}
