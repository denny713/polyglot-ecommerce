package com.ecommerce.auth.dao;

import com.ecommerce.auth.exception.AuthenticationException;
import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import com.ecommerce.auth.model.AuthToken;
import com.ecommerce.auth.model.UserCredentials;

/**
 * Contract for accessing the identity store — the equivalent of a
 * {@code Repository} in Spring Data JPA, except that the "database" here is
 * Keycloak.
 * @see com.ecommerce.auth.dao.keycloak.KeycloakIdentityProviderDao
 */
public interface IdentityProviderDao {

    /**
     * Verifies the credentials and issues a token.
     *
     * @param credentials the user's username and password
     * @return the token issued by the identity provider
     * @throws AuthenticationException              the credentials were rejected
     * @throws IdentityProviderUnavailableException the provider could not be reached
     */
    AuthToken authenticate(UserCredentials credentials);
}
