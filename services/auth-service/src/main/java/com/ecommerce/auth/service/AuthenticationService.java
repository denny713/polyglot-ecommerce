package com.ecommerce.auth.service;

import com.ecommerce.auth.exception.AuthenticationException;
import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import com.ecommerce.auth.model.AuthToken;
import com.ecommerce.auth.model.UserCredentials;

/**
 * Contract for the authentication business logic — this is what the controller
 * uses.
 *
 * <p>
 * It works with domain models ({@link UserCredentials}, {@link AuthToken}) rather
 * than HTTP DTOs, so the logic can be reused from other triggers (gRPC, a message
 * consumer, a scheduled job) without dragging JAX-RS along.
 *
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
}
