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

/** Describes the service as a whole in the OpenAPI document — the header that sits above the individual endpoints. */
@ApplicationPath("/")
@OpenAPIDefinition(
        info = @Info(
                title = "auth-service API",
                version = "1.0.0",
                description = """
                        Authentication and account management for the polygot-ecommerce platform.

                        The service is a thin, well-defined front for Keycloak. Under \
                        **Authentication** it exchanges a username and password for a token \
                        pair and ends the session a refresh token belongs to; under **Account** \
                        it creates, changes and removes the accounts those tokens belong to. It \
                        never stores credentials, sessions or profiles itself — Keycloak is the \
                        single source of truth for all three.

                        Registration does not take a password. One is generated and emailed to \
                        the address on the request, so the usual first run is \
                        `POST /api/account/register` → read the email → `POST /api/auth/login` \
                        → `PUT /api/account/password`.

                        Only `POST /api/auth/*` and `POST /api/account/register` are open. The \
                        Everything else lives under `/api/account` and acts on whichever \
                        account the bearer token names — the id comes from the `sub` claim, never \
                        from the URL, so there is no way to address somebody else's account and \
                        no administrative override. Changing a password needs the old one on top \
                        of that.

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
        tags = {
                @Tag(
                        name = "Authentication",
                        description = "Obtaining and discarding tokens"),
                @Tag(
                        name = "Account",
                        description = "Account management")
        })
@SecurityScheme(
        securitySchemeName = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = """
                The access token returned by `POST /api/auth/login`, sent as \
                `Authorization: Bearer <accessToken>`.

                Required by everything under `/api/account`, which is also where the \
                account id comes from. The other three endpoints are open by design: logging \
                in and logging out have no token to present yet or any more, and registering \
                is how a customer gets one in the first place.

                Paste a token into Swagger UI's *Authorize* dialog once and it is carried on \
                every call from there — including to the other services on the platform, which \
                all validate the same tokens.
                """)
public class OpenApiConfiguration extends Application {
}
