package com.order.api.configuration;

import com.order.api.constant.SecurityType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;

/** Describes this service to Swagger UI and springdoc. */
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
                        .title("API for Order Service")
                        .version("1.0.0")
                        .description("API documentation for Order Service application"));
    }
}
