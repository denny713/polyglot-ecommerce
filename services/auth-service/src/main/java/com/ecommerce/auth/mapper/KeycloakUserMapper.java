package com.ecommerce.auth.mapper;

import com.ecommerce.auth.dao.keycloak.dto.KeycloakCredentialRepresentation;
import com.ecommerce.auth.dao.keycloak.dto.KeycloakUserRepresentation;
import com.ecommerce.auth.model.Account;
import com.ecommerce.auth.model.AccountUpdate;
import com.ecommerce.auth.model.NewAccount;
import com.ecommerce.auth.model.RawPassword;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/** Converts between the account domain models and Keycloak's {@code UserRepresentation}. */
@ApplicationScoped
public class KeycloakUserMapper {

    /** Builds the representation for a brand new account. */
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

    /** Builds the representation for a partial update. */
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

    /** The credential body for a password change. */
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
