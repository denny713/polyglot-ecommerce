package com.ecommerce.auth.dao;

import com.ecommerce.auth.exception.AuthenticationException;
import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import com.ecommerce.auth.model.AuthToken;
import com.ecommerce.auth.model.UserCredentials;

/**
 * Contract for accessing the identity store — the equivalent of a
 * {@code Repository} in Spring Data JPA, except that the "database" here is
 * Keycloak.
 *
 * <p>
 * <strong>Why not use JPA directly against the keycloak database?</strong>
 * Passwords in the {@code CREDENTIAL} table are stored as PBKDF2 hashes with a
 * per-user salt and an iteration count kept in a JSON column; that schema is
 * internal and may change between Keycloak versions. Reading it ourselves would
 * also bypass the password policy and brute force detection and — most
 * importantly — would not produce an access token that other services can
 * verify. That is why the concrete implementation calls the Keycloak token
 * endpoint instead.
 *
 * <p>
 * This interface is what makes {@code AuthenticationService} depend on an
 * abstraction rather than on Keycloak (Dependency Inversion Principle). It
 * deliberately declares a single method so that classes which only need login
 * are not dragged into other operations (Interface Segregation Principle) — if
 * refresh token or logout support is needed later, create a separate interface.
 *
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
