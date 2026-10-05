package com.ecommerce.auth.configuration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/** Credentials of the service account this service uses to call the Keycloak Admin REST API. */
@ConfigMapping(prefix = "keycloak.admin-api")
public interface KeycloakAdminApiProperties {

    /** Client id of the confidential client whose service account is used, e.g. {@code auth-service}. */
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
