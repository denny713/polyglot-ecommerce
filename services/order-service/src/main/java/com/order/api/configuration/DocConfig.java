package com.order.api.configuration;

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
