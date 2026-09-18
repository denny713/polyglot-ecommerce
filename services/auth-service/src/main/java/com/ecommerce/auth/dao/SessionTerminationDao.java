package com.ecommerce.auth.dao;

import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import com.ecommerce.auth.exception.InvalidRefreshTokenException;
import com.ecommerce.auth.model.RefreshToken;

/**
 * Contract for ending a session at the identity provider.
 * @see com.ecommerce.auth.dao.keycloak.KeycloakSessionTerminationDao
 */
public interface SessionTerminationDao {

    /**
     * Ends the session the refresh token belongs to.
     * @param refreshToken the refresh token issued at login
     * @throws InvalidRefreshTokenException         the token was expired, unknown, or already revoked
     * @throws IdentityProviderUnavailableException the provider could not be reached
     */
    void revoke(RefreshToken refreshToken);
}