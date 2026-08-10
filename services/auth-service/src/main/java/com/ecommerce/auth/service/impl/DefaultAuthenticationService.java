package com.ecommerce.auth.service.impl;

import com.ecommerce.auth.dao.IdentityProviderDao;
import com.ecommerce.auth.exception.AuthenticationException;
import com.ecommerce.auth.model.AuthToken;
import com.ecommerce.auth.model.UserCredentials;
import com.ecommerce.auth.service.AuthenticationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Objects;

/**
 * Implementasi default dari {@link AuthenticationService}.
 *
 * <p>
 * Namanya sengaja bukan "Keycloak...": kelas ini bergantung pada
 * {@link IdentityProviderDao}, bukan pada Keycloak, jadi provider apa pun yang
 * dipasang di baliknya tidak mengubah kelas ini (Dependency Inversion
 * Principle).
 *
 * <p>
 * Constructor injection dipakai supaya dependensinya eksplisit dan kelas ini
 * bisa di-instansiasi langsung di unit test tanpa container CDI.
 */
@ApplicationScoped
public class DefaultAuthenticationService implements AuthenticationService {

    private static final Logger LOG = Logger.getLogger(DefaultAuthenticationService.class);

    private final IdentityProviderDao identityProviderDao;

    @Inject
    public DefaultAuthenticationService(IdentityProviderDao identityProviderDao) {
        this.identityProviderDao = identityProviderDao;
    }

    @Override
    public AuthToken doLogin(UserCredentials credentials) {
        Objects.requireNonNull(credentials, "credentials must not be null");

        LOG.debugf("Authenticating user '%s'", credentials.username());
        try {
            AuthToken token = identityProviderDao.authenticate(credentials);
            LOG.infof("User '%s' logged in successfully", credentials.username());
            return token;
        } catch (AuthenticationException e) {
            // Dicatat di sini, bukan di controller: percobaan login gagal adalah
            // kejadian bisnis (bahan audit trail), bukan sekadar error HTTP.
            LOG.warnf("Login failed for user '%s': %s", credentials.username(), e.errorCode());
            throw e;
        }
    }
}
