package com.ecommerce.auth.dao.keycloak;

import com.ecommerce.auth.configuration.KeycloakAuthProperties;
import com.ecommerce.auth.dao.AccountProviderDao;
import com.ecommerce.auth.dao.keycloak.dto.KeycloakRoleRepresentation;
import com.ecommerce.auth.dao.keycloak.dto.KeycloakUserRepresentation;
import com.ecommerce.auth.exception.AccountException;
import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import com.ecommerce.auth.mapper.KeycloakUserMapper;
import com.ecommerce.auth.model.Account;
import com.ecommerce.auth.model.AccountRole;
import com.ecommerce.auth.model.AccountUpdate;
import com.ecommerce.auth.model.NewAccount;
import com.ecommerce.auth.model.RawPassword;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.List;

/** Implementation of {@link AccountProviderDao} that uses Keycloak's Admin REST API. */
@ApplicationScoped
public class KeycloakAccountProviderDao implements AccountProviderDao {

    private static final Logger LOG = Logger.getLogger(KeycloakAccountProviderDao.class);

    private final KeycloakAdminClient adminClient;
    private final KeycloakAdminTokenProvider tokenProvider;
    private final KeycloakAuthProperties properties;
    private final KeycloakUserMapper userMapper;

    @Inject
    public KeycloakAccountProviderDao(@RestClient KeycloakAdminClient adminClient,
                                      KeycloakAdminTokenProvider tokenProvider,
                                      KeycloakAuthProperties properties,
                                      KeycloakUserMapper userMapper) {
        this.adminClient = adminClient;
        this.tokenProvider = tokenProvider;
        this.properties = properties;
        this.userMapper = userMapper;
    }

    /** Creates the account, then reads it back. */
    @Override
    public Account doCreate(NewAccount newAccount, RawPassword password) {
        String bearerToken = tokenProvider.bearerToken();

        run(() -> adminClient.createUser(bearerToken, properties.realm(),
                userMapper.toRepresentation(newAccount, password)));

        List<KeycloakUserRepresentation> found = call(() -> adminClient.findByUsername(
                bearerToken, properties.realm(), newAccount.username(), true));

        if (found == null || found.isEmpty()) {
            // Keycloak accepted the create and then did not return the account.
            // Nothing the caller can do about that, so it is an outage, not a 4xx.
            LOG.errorf("Keycloak accepted the creation of '%s' but did not return it on lookup",
                    newAccount.username());
            throw new IdentityProviderUnavailableException(
                    "The account was created but could not be read back from the identity provider");
        }

        Account account = userMapper.toDomain(found.get(0));
        LOG.infof("Registered account '%s' (id %s)", account.username(), account.id());
        return account;
    }

    /** Looks the role up in the realm, then maps it onto the account. */
    @Override
    public void doAssignRole(String accountId, AccountRole role) {
        String bearerToken = tokenProvider.bearerToken();

        KeycloakRoleRepresentation realmRole = call(() ->
                adminClient.findRealmRole(bearerToken, properties.realm(), role.roleName()));

        if (realmRole == null || realmRole.id() == null) {
            // A 404 would already have been translated by the error mapper, so
            // this is the stranger case: a 200 with nothing usable in it.
            LOG.errorf("Keycloak returned no usable definition for realm role '%s'", role.roleName());
            throw new IdentityProviderUnavailableException(
                    "The identity provider did not return the '" + role.roleName() + "' role");
        }

        run(() -> adminClient.addRealmRoles(bearerToken, properties.realm(), accountId, List.of(realmRole)));

        LOG.infof("Granted role '%s' to account %s", role.roleName(), accountId);
    }

    @Override
    public Account doFindById(String accountId) {
        String bearerToken = tokenProvider.bearerToken();

        return userMapper.toDomain(call(() ->
                adminClient.findById(bearerToken, properties.realm(), accountId)));
    }

    @Override
    public void doChangePassword(String accountId, RawPassword newPassword) {
        String bearerToken = tokenProvider.bearerToken();

        run(() -> adminClient.resetPassword(bearerToken, properties.realm(), accountId,
                userMapper.toCredential(newPassword)));

        // The password itself never appears here — see RawPassword#toString.
        LOG.infof("Changed the password of account %s", accountId);
    }

    @Override
    public void doUpdate(String accountId, AccountUpdate update) {
        String bearerToken = tokenProvider.bearerToken();

        run(() -> adminClient.updateUser(bearerToken, properties.realm(), accountId,
                userMapper.toRepresentation(update)));

        LOG.infof("Updated account %s", accountId);
    }

    @Override
    public void doDelete(String accountId) {
        String bearerToken = tokenProvider.bearerToken();

        run(() -> adminClient.deleteUser(bearerToken, properties.realm(), accountId));

        LOG.infof("Deleted account %s", accountId);
    }

    /**
     * Runs an admin call, translating anything the
     * {@link KeycloakAdminErrorResponseMapper} did not already turn into a domain
     * exception — a refused connection, a timeout, an unreadable body.
     */
    private <T> T call(AdminCall<T> adminCall) {
        try {
            return adminCall.execute();
        } catch (AccountException | IdentityProviderUnavailableException e) {
            // Already translated by KeycloakAdminErrorResponseMapper — rethrow as-is.
            throw e;
        } catch (ProcessingException | WebApplicationException e) {
            LOG.errorf(e, "Failed to reach the Keycloak admin API for realm '%s'", properties.realm());
            throw new IdentityProviderUnavailableException("Could not reach the identity provider", e);
        }
    }

    /**
     * The {@code void} form. A separate name rather than an overload of
     * {@code call}: a lambda wrapping a void method is applicable to both
     * signatures during overload resolution, and the compiler is entitled to
     * call that ambiguous.
     */
    private void run(Runnable adminCall) {
        call(() -> {
            adminCall.run();
            return null;
        });
    }

    @FunctionalInterface
    private interface AdminCall<T> {
        T execute();
    }
}
