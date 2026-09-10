package com.ecommerce.auth.security;

import com.ecommerce.auth.exception.AccountAccessDeniedException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.security.Principal;

/**
 * The account the current bearer token belongs to.
 *
 * <p>
 * This replaces the guard that used to compare an {@code {accountId}} path
 * segment against the token. Once the rule became "only your own", that
 * comparison could only ever have two outcomes — equal, or a 403 — so the path
 * segment carried no information the token did not already have, and every
 * caller had to be told its own id before it could use the API. Taking the id
 * from the token instead makes the wrong answer unrepresentable: there is no
 * longer a way to ask about somebody else's account, so there is nothing left
 * to check.
 *
 * <p>
 * That is the difference between a rule that is enforced and one that cannot be
 * broken. A comparison can be forgotten on a new endpoint; a missing path
 * parameter cannot.
 */
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
     *
     * <p>
     * In practice this never fails: {@code @Authenticated} and the
     * {@code authenticated} path policy both stop an anonymous request before it
     * reaches an endpoint that calls this, and OpenID Connect requires
     * {@code sub} on every token. It is still checked rather than assumed,
     * because the alternative is passing {@code null} down into the DAO and
     * turning an impossible situation into a confusing 500.
     *
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
