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
- the `@Operation` and `@APIResponse` annotations on `AuthController` — one entry
  per status code the exception mappers can produce;
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
