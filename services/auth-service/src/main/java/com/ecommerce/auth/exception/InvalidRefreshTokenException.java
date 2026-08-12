package com.ecommerce.auth.exception;

import com.ecommerce.auth.enums.AuthErrorCode;

/**
 * The refresh token presented was rejected by the identity provider: it has
 * expired, was already used to log out, or belongs to a session that no longer
 * exists.
 *
 * <p>
 * Note that logout does <em>not</em> surface this to the client — see
 * {@code DefaultAuthenticationService#doLogout}, which treats an already-dead
 * session as a successful logout. The type exists so the DAO can report
 * <em>why</em> Keycloak said no without the caller having to parse an OAuth2
 * error string, and so a future endpoint that genuinely needs to fail on it
 * (token refresh, for instance) already has the right exception to throw.
 */
public class InvalidRefreshTokenException extends AuthenticationException {

    public InvalidRefreshTokenException(String message) {
        super(AuthErrorCode.INVALID_REFRESH_TOKEN, message);
    }
}