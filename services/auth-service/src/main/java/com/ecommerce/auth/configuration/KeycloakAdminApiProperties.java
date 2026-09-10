package com.ecommerce.auth.configuration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Credentials of the service account this service uses to call the Keycloak
 * Admin REST API.
 *
 * <p>
 * These are <em>not</em> the credentials in {@link KeycloakAuthProperties}. That
 * one is the public {@code ecommerce-app} client, used to exchange a user's
 * password for a token; this one is the confidential {@code auth-service}
 * client, whose service account holds the {@code realm-management} roles
 * needed to create, update and delete users. Keeping them apart is what stops
 * the browser-facing client from ever being able to manage accounts.
 *
 * <p>
 * The prefix is {@code keycloak.admin-api} rather than {@code keycloak.admin} on
 * purpose: the latter would collide with the {@code KEYCLOAK_ADMIN} variable
 * that {@code app/.env} uses for Keycloak's own bootstrap admin user, and a
 * stray export of that variable would then fail startup with an unmapped
 * property.
 *
 * <p>
 * The realm is deliberately absent — accounts are managed in the same realm
 * users log in to, so it is read from {@link KeycloakAuthProperties#realm()} and
 * there is no second place to keep in sync.
 */
@ConfigMapping(prefix = "keycloak.admin-api")
public interface KeycloakAdminApiProperties {

    /**
     * Client id of the confidential client whose service account is used, e.g.
     * {@code auth-service}.
     */
    String clientId();

    /**
     * Its secret. Mandatory, unlike {@link KeycloakAuthProperties#clientSecret()}:
     * a service account only exists on a confidential client.
     */
    String clientSecret();

    @WithDefault("client_credentials")
    String grantType();

    /**
     * Seconds to treat a still-valid token as expired, so a token is never sent
     * on a request that outlives it by the time Keycloak reads it.
     */
    @WithDefault("30")
    long tokenExpiryLeewaySeconds();
}
