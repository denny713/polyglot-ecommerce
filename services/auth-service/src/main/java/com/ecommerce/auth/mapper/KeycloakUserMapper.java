package com.ecommerce.auth.mapper;

import com.ecommerce.auth.dao.keycloak.dto.KeycloakCredentialRepresentation;
import com.ecommerce.auth.dao.keycloak.dto.KeycloakUserRepresentation;
import com.ecommerce.auth.model.Account;
import com.ecommerce.auth.model.AccountUpdate;
import com.ecommerce.auth.model.NewAccount;
import com.ecommerce.auth.model.RawPassword;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Converts between the account domain models and Keycloak's
 * {@code UserRepresentation}.
 *
 * <p>
 * Kept out of the DAO for the same reason as {@link KeycloakTokenMapper}: the
 * DAO changes when the way it is called changes, the mapper changes when the
 * payload shape changes (Single Responsibility Principle).
 */
@ApplicationScoped
public class KeycloakUserMapper {

    /**
     * Builds the representation for a brand new account.
     *
     * <p>
     * {@code emailVerified} is {@code false} because nothing has verified it —
     * claiming otherwise would be a lie the rest of the platform then trusts. It
     * costs nothing at login: the {@code ecommerce} realm does not require a
     * verified email, and the "Verify Profile" required action that Keycloak 26
     * enables by default is satisfied because email and both names are always
     * present here.
     *
     * <p>
     * {@code requiredActions} is explicitly empty so the account is usable
     * immediately rather than stopping at a "set up your account" screen on
     * first login.
     */
    public KeycloakUserRepresentation toRepresentation(NewAccount newAccount, RawPassword password) {
        if (newAccount == null) {
            throw new IllegalArgumentException("newAccount must not be null");
        }

        if (password == null) {
            throw new IllegalArgumentException("password must not be null");
        }

        return new KeycloakUserRepresentation(
                null,
                newAccount.username(),
                newAccount.email(),
                newAccount.firstName(),
                newAccount.lastName(),
                true,
                true,
                List.of(),
                List.of(KeycloakCredentialRepresentation.permanentPassword(password.value())));
    }

    /**
     * Builds the representation for a partial update.
     *
     * <p>
     * Every field not being changed stays {@code null} and is dropped from the
     * JSON by {@code @JsonInclude(NON_NULL)}, which is what tells Keycloak to
     * leave the stored value alone. Note in particular that {@code enabled} is
     * left out: sending {@code false} here would silently disable the account,
     * and sending {@code true} would silently re-enable one an administrator had
     * disabled on purpose.
     */
    public KeycloakUserRepresentation toRepresentation(AccountUpdate update) {
        if (update == null) {
            throw new IllegalArgumentException("update must not be null");
        }

        return new KeycloakUserRepresentation(
                null,
                null,
                update.email(),
                update.firstName(),
                update.lastName(),
                null,
                null,
                null,
                null);
    }

    /**
     * The credential body for a password change.
     *
     * <p>
     * {@code temporary} is {@code false} here as it is on create: the holder
     * just chose this value themselves, so forcing them to choose again at the
     * next login would be nothing but an obstacle.
     */
    public KeycloakCredentialRepresentation toCredential(RawPassword password) {
        if (password == null) {
            throw new IllegalArgumentException("password must not be null");
        }

        return KeycloakCredentialRepresentation.permanentPassword(password.value());
    }

    public Account toDomain(KeycloakUserRepresentation representation) {
        if (representation == null) {
            throw new IllegalArgumentException("Keycloak user representation must not be null");
        }

        return new Account(
                representation.id(),
                representation.username(),
                representation.email(),
                representation.firstName(),
                representation.lastName(),
                Boolean.TRUE.equals(representation.enabled()));
    }
}
