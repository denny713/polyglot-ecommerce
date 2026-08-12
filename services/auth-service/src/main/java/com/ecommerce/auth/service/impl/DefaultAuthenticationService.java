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
 * Default implementation of {@link AuthenticationService}.
 *
 * <p>
 * It is deliberately not named "Keycloak...": this class depends on
 * {@link IdentityProviderDao}, not on Keycloak, so whichever provider sits behind
 * it does not change this class (Dependency Inversion Principle).
 *
 * <p>
 * Constructor injection is used to keep the dependencies explicit and to let the
 * class be instantiated directly in unit tests without a CDI container.
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
            // Logged here, not in the controller: a failed login attempt is a
            // business event (audit trail material), not merely an HTTP error.
            LOG.warnf("Login failed for user '%s': %s", credentials.username(), e.errorCode());
            throw e;
        }
    }
}
