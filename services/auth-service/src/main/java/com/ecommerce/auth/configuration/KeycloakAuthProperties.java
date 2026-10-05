package com.ecommerce.auth.configuration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

/** Configuration properties for Keycloak authentication. */
@ConfigMapping(prefix = "keycloak.auth")
public interface KeycloakAuthProperties {

    String realm();

    String clientId();

    Optional<String> clientSecret();

    Optional<String> scope();

    @WithDefault("password")
    String grantType();
}
