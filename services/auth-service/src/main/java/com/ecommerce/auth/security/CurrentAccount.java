package com.ecommerce.auth.security;

import com.ecommerce.auth.exception.AccountAccessDeniedException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.security.Principal;

/** The account the current bearer token belongs to. */
@ApplicationScoped
public class CurrentAccount {

    private final SecurityIdentity identity;

    @Inject
    public CurrentAccount(SecurityIdentity identity) {
        this.identity = identity;
    }

    /**
     * The {@code sub} claim of the bearer token, which is Keycloak's id for the
     * account.
     * @return the caller's account id
     * @throws AccountAccessDeniedException the caller was authenticated by
     *                                      something that carries no subject
     */
    public String id() {
        Principal principal = identity.getPrincipal();

        if (principal instanceof JsonWebToken jwt) {
            String subject = jwt.getSubject();
            if (subject != null && !subject.isBlank()) {
                return subject;
            }
        }

        throw new AccountAccessDeniedException("The access token does not identify an account");
    }
}
