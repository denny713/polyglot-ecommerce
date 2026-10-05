package com.ecommerce.auth.service;

import com.ecommerce.auth.exception.AuthenticationException;
import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import com.ecommerce.auth.model.AuthToken;
import com.ecommerce.auth.model.RefreshToken;
import com.ecommerce.auth.model.UserCredentials;

/**
 * Contract for the authentication business logic — this is what the controller
 * uses.
 * @see com.ecommerce.auth.service.impl.DefaultAuthenticationService
 */
public interface AuthenticationService {

    /**
     * Performs a login with a username and password.
     *
     * @param credentials the user's credentials
     * @return the token issued by the identity provider
     * @throws AuthenticationException              the credentials were rejected
     * @throws IdentityProviderUnavailableException the identity provider is unavailable
     */
    AuthToken doLogin(UserCredentials credentials);

    /**
     * Ends the session the refresh token belongs to.
     * @param refreshToken the refresh token issued at login
     * @throws IdentityProviderUnavailableException the identity provider is unavailable
     */
    void doLogout(RefreshToken refreshToken);
}
