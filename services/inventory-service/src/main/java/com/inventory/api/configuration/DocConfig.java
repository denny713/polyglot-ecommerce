package com.inventory.api.configuration;

import com.inventory.api.constant.SecurityType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;

/**
 * Describes this service to Swagger UI and springdoc.
 * <p>
 * Besides the title and version it declares the {@code bearerAuth} scheme, which is
 * what puts the <em>Authorize</em> button in Swagger UI. Without it every "Try it
 * out" would be sent without an {@code Authorization} header and rejected by
 * {@code TokenFilter} with 401.
 */
@Configuration
public class DocConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes(SecurityType.SECURITY_SCHEME,
                                new SecurityScheme()
                                        .name(SecurityType.SECURITY_SCHEME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(SecurityType.SECURITY_SCHEME))
                .info(new Info()
                        .title("API for Inventory Service")
                        .version("1.0.0")
                        .description("API documentation for Inventory Service application"));
    }
}
