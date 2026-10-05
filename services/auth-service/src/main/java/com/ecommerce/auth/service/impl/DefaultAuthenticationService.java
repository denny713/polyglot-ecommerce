package com.ecommerce.auth.service.impl;

import com.ecommerce.auth.dao.IdentityProviderDao;
import com.ecommerce.auth.dao.SessionTerminationDao;
import com.ecommerce.auth.exception.AuthenticationException;
import com.ecommerce.auth.exception.InvalidRefreshTokenException;
import com.ecommerce.auth.model.AuthToken;
import com.ecommerce.auth.model.RefreshToken;
import com.ecommerce.auth.model.UserCredentials;
import com.ecommerce.auth.service.AuthenticationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Objects;

/** Default implementation of {@link AuthenticationService}. */
@ApplicationScoped
public class DefaultAuthenticationService implements AuthenticationService {

    private static final Logger LOG = Logger.getLogger(DefaultAuthenticationService.class);

    private final IdentityProviderDao identityProviderDao;
    private final SessionTerminationDao sessionTerminationDao;

    @Inject
    public DefaultAuthenticationService(
            IdentityProviderDao identityProviderDao,
            SessionTerminationDao sessionTerminationDao
    ) {
        this.identityProviderDao = identityProviderDao;
        this.sessionTerminationDao = sessionTerminationDao;
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

    @Override
    public void doLogout(RefreshToken refreshToken) {
        Objects.requireNonNull(refreshToken, "refreshToken must not be null");

        try {
            sessionTerminationDao.revoke(refreshToken);
            LOG.debug("Session ended successfully");
        } catch (InvalidRefreshTokenException e) {
            // The session was already gone, so the caller's goal is met — swallowing
            // this is a business decision, which is why it is made here and not in
            // the DAO. It also keeps the endpoint from doubling as an oracle that
            // tells an attacker whether a stolen refresh token is still live.
            LOG.debug("Logout requested for a session that had already ended");
        }
    }
}
