package com.ecommerce.auth.security;

import com.ecommerce.auth.dao.IdentityProviderDao;
import com.ecommerce.auth.dao.SessionTerminationDao;
import com.ecommerce.auth.exception.AuthenticationException;
import com.ecommerce.auth.exception.InvalidRefreshTokenException;
import com.ecommerce.auth.model.AuthToken;
import com.ecommerce.auth.model.RefreshToken;
import com.ecommerce.auth.model.UserCredentials;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/** Checks that whoever is asking to change a password knows the current one. */
@ApplicationScoped
public class CurrentPasswordVerifier {

    private static final Logger LOG = Logger.getLogger(CurrentPasswordVerifier.class);

    private final IdentityProviderDao identityProviderDao;
    private final SessionTerminationDao sessionTerminationDao;

    @Inject
    public CurrentPasswordVerifier(IdentityProviderDao identityProviderDao,
                                   SessionTerminationDao sessionTerminationDao) {
        this.identityProviderDao = identityProviderDao;
        this.sessionTerminationDao = sessionTerminationDao;
    }

    /**
     * @param username        the account's login name
     * @param currentPassword the password to check
     * @throws AuthenticationException the password is wrong, or the account is
     *                                 locked or disabled
     */
    public void verify(String username, String currentPassword) {
        AuthToken token = identityProviderDao.authenticate(new UserCredentials(username, currentPassword));

        discard(token, username);
    }

    private void discard(AuthToken token, String username) {
        if (token == null || token.refreshToken() == null || token.refreshToken().isBlank()) {
            return;
        }

        try {
            sessionTerminationDao.revoke(new RefreshToken(token.refreshToken()));
        } catch (InvalidRefreshTokenException e) {
            // The session is already gone, which is the outcome we wanted.
            LOG.debug("The verification session had already ended");
        } catch (RuntimeException e) {
            // Failing to tidy up must not fail the password change itself: the
            // password was correct, and the stray session expires on its own.
            LOG.warnf(e, "Could not end the session opened to verify a password for '%s'", username);
        }
    }
}
