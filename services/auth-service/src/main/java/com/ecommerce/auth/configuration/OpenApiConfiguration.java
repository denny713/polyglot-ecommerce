package com.ecommerce.auth.configuration;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.info.License;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.servers.Server;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Describes the service as a whole in the OpenAPI document — the header that
 * sits above the individual endpoints.
 *
 * <p>
 * This metadata is kept in code rather than in {@code application.properties}
 * for the same reason the DTOs carry their own annotations: the description of
 * an endpoint belongs next to the endpoint, and a rename or a new tag is then
 * caught by the compiler instead of silently drifting out of date.
 *
 * <p>
 * The class extends {@link Application} only because that is where the
 * MicroProfile OpenAPI specification expects {@link OpenAPIDefinition} to live.
 * It deliberately does not override {@code getClasses()} or
 * {@code getSingletons()} — doing so would switch Quarkus from scanning for
 * resources to using only what is listed here, and the controller would
 * disappear from the running application.
 *
 * <p>
 * The document is served at {@code /q/openapi} and rendered by Swagger UI at
 * {@code /q/swagger-ui}; both paths are configured in
 * {@code application.properties}.
 */
@ApplicationPath("/")
@OpenAPIDefinition(
        info = @Info(
                title = "auth-service API",
                version = "1.0.0",
                description = """
                        Authentication for the polygot-ecommerce platform.

                        The service is a thin, well-defined front for Keycloak: it exchanges a \
                        username and password for a token pair, and ends the session a refresh \
                        token belongs to. It never stores credentials or sessions itself.

                        Every failure answers with the same error body — `status`, `error`, \
                        `message`, an optional per-field `details` list, and a `timestamp` — so \
                        a client can handle all of them with one code path. The `error` field \
                        is the stable, machine-readable part; `message` is for humans and may \
                        be reworded without notice.
                        """,
                contact = @Contact(
                        name = "polygot-ecommerce",
                        url = "https://github.com/denny713/polygot-ecommerce"),
                license = @License(
                        name = "MIT",
                        url = "https://opensource.org/licenses/MIT")),
        servers = {
                @Server(url = "http://localhost:7110", description = "Local development"),
                @Server(url = "http://auth-service:7110", description = "Inside the compose network")
        },
        tags = @Tag(
                name = "Authentication",
                description = "Obtaining and discarding tokens"))
@SecurityScheme(
        securitySchemeName = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = """
                The access token returned by `POST /api/auth/login`, sent as \
                `Authorization: Bearer <accessToken>`.

                Neither endpoint on this service requires it — logging in and logging out are \
                both open by design — but the other services on the platform do, and pasting \
                the token into Swagger UI's *Authorize* dialog makes it easy to carry across.
                """)
public class OpenApiConfiguration extends Application {
}
