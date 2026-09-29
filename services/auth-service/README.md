# auth-service

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## API documentation (Swagger)

The service publishes an OpenAPI 3.1 document and renders it with Swagger UI.
With the application running on its default port:

| What | Where |
| --- | --- |
| Swagger UI | <http://localhost:7110/q/swagger-ui> |
| OpenAPI document (YAML) | <http://localhost:7110/q/openapi> |
| OpenAPI document (JSON) | <http://localhost:7110/q/openapi?format=json> |

Both paths are already covered by the `/q/*` entry in
`QUARKUS_HTTP_AUTH_PERMISSION_PUBLIC_PATHS`, so no token is needed to reach them.

`./mvnw package` also writes the document to `target/openapi/openapi.{json,yaml}`,
which is the copy to hand to a client generator or to commit as an API contract.

The document is assembled from three places:

- `com.ecommerce.auth.configuration.OpenApiConfiguration` — title, version,
  description, servers and the `bearerAuth` security scheme;
- the `@Operation` and `@APIResponse` annotations on `AuthController` and
  `AccountController` — one entry per status code the exception mappers can
  produce;
- the `@Schema` annotations on the request and response DTOs, plus the Bean
  Validation constraints already on them, which SmallRye turns into `required`,
  `maxLength` and friends on its own.

Swagger UI is normally a dev-mode-only feature in Quarkus. It is kept in the
packaged application through `quarkus.swagger-ui.always-include=true` in
`src/main/resources/application.properties` — set that to `false` for a
deployment where the schema should not be browsable. Note that this, like the
other properties in that file, is fixed while the application is being built:
`Dockerfile.multistage` copies only `pom.xml` and `src/` into the build stage, so
the `.env` file cannot override it.

## Endpoints

| Method | Path | Token | What it does |
| --- | --- | --- | --- |
| `POST` | `/api/auth/login` | — | Exchange username and password for a token pair |
| `POST` | `/api/auth/logout` | — | End the session a refresh token belongs to |
| `POST` | `/api/account/register` | — | Create an account; its password is generated and emailed |
| `GET` | `/api/account` | required | Read the account the token belongs to |
| `PUT` | `/api/account` | required | Change its profile fields |
| `PUT` | `/api/account/password` | required | Replace its password, given the old one |
| `DELETE` | `/api/account` | required | Delete it |

The usual first run for a new customer:

```
POST /api/account/register     -> 201, password sent by email
(read the email)
POST /api/auth/login           -> token pair
PUT  /api/account/password  -> 204, replace the emailed password
```

### There is no account id in any URL

Every authenticated endpoint acts on ``, and resolves that to the `sub`
claim of the bearer token. No path parameter, no id in a body.

This started out as `/api/account/{accountId}` with a check that the id matched
the token. That works, but it is the weaker shape: a URL that *can* name
somebody else's account needs a comparison to reject it, and a comparison is
something the next endpoint can forget. Taking the id from the token instead
makes the wrong request impossible to phrase, so there is nothing left to
enforce — and a client never has to be told its own id before it can call the
API.

### Registration does not take a password

The caller does not choose one and never sees it. The service generates eight
characters from `SecureRandom` — guaranteed to contain an upper-case letter, a
lower-case letter, a digit and a special character, so the realm policy
(`length(8) and upperCase(1) and lowerCase(1) and specialChars(1)`, which
Keycloak applies to admin-set passwords too) can never reject it — and mails
them to the address on the request.

Two consequences worth knowing:

- **The email address is load-bearing.** A typo does not merely inconvenience
  the new customer, it makes the account unusable, because the mailbox is the
  only place the password ever appears.
- **Registration is all-or-nothing.** If the broker does not accept the email, the account is
  deleted again and the call answers `503 NOTIFICATION_UNAVAILABLE`. Otherwise
  it would sit there with a password nobody knows, holding the username and
  address against the retry.

Characters that are hard to tell apart in an email — `O`/`0`, `l`/`1`/`I` — are
excluded, since the value is transcribed by hand.

### Changing a password needs the old one

`PUT /api/account/password` requires a bearer token **and** `oldPassword`.
The token says which account is being changed; the old password says you are
the person who owns it. Without the second, a stolen token would be a
permanently stolen account.

The check is a real login attempt against Keycloak, so it goes through the
realm's brute force detection: the endpoint cannot be used as an offline
password oracle, and enough wrong guesses answer `429 ACCOUNT_LOCKED`. The
session that attempt opens is ended immediately and never reaches the caller.

Tokens already issued keep working until they expire — a JWT cannot be recalled
— so a password change does not by itself end sessions elsewhere. Call
`POST /api/auth/logout` with each refresh token for that.

### Who may change which account

Everything except register needs a bearer token, stated twice on purpose:

| Where | What it does | Answers |
| --- | --- | --- |
| `quarkus.http.auth.permission.authenticated` on `/*` | Stops an anonymous request before it reaches the controller | `401` |
| `@Authenticated` on each method | Says the same in code, so the rule survives a configuration file being edited or missing | `401` |

There is no third check any more, and that is the point: with the id coming
from the token there is no such thing as a request for somebody else's account.

**No administrative override exists.** Holding the `admin` realm role changes
nothing here — `` is ``. Support and operations work goes through the
Keycloak admin console instead, where it lands in Keycloak's own audit log
rather than disappearing into this service's.

Register is the one write that is open, because a new customer has no token
yet. It is listed explicitly in `quarkus.http.auth.permission.public.paths`.

Those permissions live in `src/main/resources/application.properties`, not in
`.env`: they are the security posture of the service rather than a credential,
so they belong in version control next to the endpoints they protect.

### Keycloak setup the account endpoints need

They are a front for the Keycloak Admin REST API, which this service calls with
the **service account of the confidential `auth-service` client** — not with the
public `ecommerce-app` client used for logins. Two things have to be in place,
and `app/init/keycloak-init.sh` sets up both:

1. the service account holds the `realm-management` roles `manage-users` and
   `view-users`, or every account call answers `503`;
2. the public client has an audience mapper for `auth-service`, or a customer's
   own token is refused with `401` by this service's audience validation.

The client secret goes into `.env` twice — once as
`QUARKUS_OIDC_CREDENTIALS_SECRET` for validating incoming tokens, and once as
`KEYCLOAK_ADMIN_API_CLIENT_SECRET` for making outgoing admin calls. The init
script prints it at the end of its run.

### Where the account emails go

auth-service does not talk to SMTP. It publishes an account event to RabbitMQ
(exchange `notification.exchange`, routing key `notification.account`) and
`services/notification-service` turns it into an email:

| Event | Sent when | Blocks the request? |
| --- | --- | --- |
| `ACCOUNT_REGISTERED` | `POST /api/account` — carries the generated password | Yes: waits for the broker's confirm, and the account is deleted again if it does not come |
| `ACCOUNT_UPDATED` | `PUT /api/account` — lists the changed fields | No: best effort, a failure is only logged |
| `PASSWORD_CHANGED` | `PUT /api/account/password` | No |
| `ACCOUNT_DELETED` | `DELETE /api/account` | No |

The broker connection comes from `RABBITMQ_HOST` / `_PORT` / `_USERNAME` /
`_PASSWORD` in `.env`; the channel itself is in `application.properties`. The
test profile swaps the channel for the in-memory connector, so the suite needs
no broker. With the notification service pointed at Mailpit (`SMTP_HOST=localhost`,
`SMTP_PORT=1025`), the emails can be read at <http://localhost:8025>.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/auth-service-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Related Guides

- REST Jackson ([guide](https://quarkus.io/guides/rest#json-serialisation)): Jackson serialization support for Quarkus REST. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it
- OpenID Connect ([guide](https://quarkus.io/guides/security-openid-connect)): Secure applications with OpenID Connect and OAuth 2.0 using bearer tokens and authorization code flow
- SmallRye OpenAPI ([guide](https://quarkus.io/guides/openapi-swaggerui)): Document your REST APIs with OpenAPI - comes with Swagger UI
