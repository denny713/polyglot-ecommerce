package com.ecommerce.auth.dao;

import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import com.ecommerce.auth.exception.InvalidRefreshTokenException;
import com.ecommerce.auth.model.RefreshToken;

/**
 * Contract for ending a session at the identity provider.
 *
 * <p>
 * Deliberately a separate interface from {@link IdentityProviderDao} rather than
 * a second method on it: a class that only logs users in has no business being
 * recompiled — or mocked — because the logout signature changed (Interface
 * Segregation Principle). {@code IdentityProviderDao} says as much in its own
 * Javadoc.
 *
 * @see com.ecommerce.auth.dao.keycloak.KeycloakSessionTerminationDao
 */
public interface SessionTerminationDao {

    /**
     * Ends the session the refresh token belongs to.
     *
     * <p>
     * After this returns, the refresh token can no longer be exchanged for a new
     * access token. Access tokens already issued for the session stay valid until
     * they expire — they are self-contained JWTs and no provider can recall them.
     *
     * @param refreshToken the refresh token issued at login
     * @throws InvalidRefreshTokenException         the token was expired, unknown, or already revoked
     * @throws IdentityProviderUnavailableException the provider could not be reached
     */
    void revoke(RefreshToken refreshToken);
}