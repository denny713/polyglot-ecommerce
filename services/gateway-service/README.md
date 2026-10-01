# gateway-service

The single front door of polygot-ecommerce, built on [KrakenD](https://www.krakend.io/)
2.10. It is stateless and declarative: there is no code here, only the
configuration that maps public routes onto backend services.

The gateway validates the Keycloak access token and the realm role of every
protected route before the request reaches a service, applies CORS and rate
limits, and forwards status codes and bodies unchanged. The services still
validate the token themselves; the gateway check is a first line, not a
replacement.

There is one set of settings. Nothing is split per environment: what differs
between machines (backend hosts, JWKS URL, issuer) is overridden with
environment variables at container start.

## Layout

```
configuration/
├── krakend.tmpl                    # root template: global settings + the loop over all services
├── settings/                       # the values
│   ├── env.json                    # port, timeouts, CORS, JWT, rate limit profiles
│   ├── auth_service.json           # host + endpoints of auth-service
│   ├── order_service.json
│   ├── product_service.json
│   ├── inventory_service.json
│   └── recommendation_service.json
├── templates/                      # reusable pieces
│   ├── default_backend.tmpl        # renders ONE endpoint from a settings entry
│   ├── jwt_auth.tmpl               # auth/validator against Keycloak
│   ├── rate_limit.tmpl             # qos/ratelimit/router
│   └── extra_config.tmpl           # router + CORS
└── partials/                       # static snippets
    ├── ih_default.tmpl             # input_headers for public routes
    └── ih_authenticated_user.tmpl  # input_headers for protected routes
```

This is KrakenD's [flexible configuration](https://www.krakend.io/docs/configuration/flexible-config/):
the binary renders `krakend.tmpl` as a Go template at startup and parses the
result. Each file in `settings/` becomes a top-level variable named after the
file, so `settings/env.json` is `{{ .env }}`.

The rendered configuration is written to `/tmp/krakend.json` inside the
container (`FC_OUT`). Look there first when a change does not behave the way it
reads:

```shell script
docker exec gateway cat /tmp/krakend.json
```

The image also runs `krakend check` at build time, so a broken template fails
`docker build` instead of the container start.

## Settings format

`settings/<service>.json`:

```json
{
  "host": "http://host.docker.internal:7120",
  "host_env": "ORDER_SERVICE_HOST",
  "timeout": "10s",
  "swagger": { "endpoint": "/docs/order/openapi.json", "backend": "/api/v3/api-docs" },
  "endpoints": [
    { "endpoint": "/api/order/cart", "method": "POST", "backend": "/api/order/cart",
      "auth": true, "roles": ["user", "admin"] }
  ]
}
```

| Field (endpoint) | Meaning                                                      | Default                  |
| ---------------- | ------------------------------------------------------------ | ------------------------ |
| `endpoint`       | public path on the gateway                                   | required                 |
| `method`         | HTTP method                                                  | required                 |
| `backend`        | path on the service                                          | required                 |
| `auth`           | validate the JWT at the gateway                              | `false`                  |
| `roles`          | Keycloak realm roles accepted (`realm_access.roles`)         | `env.jwt.default_roles`  |
| `rate_limit`     | name of a profile in `env.rate_limit` (e.g. `"auth"`)        | none                     |
| `timeout`        | overrides the service `timeout`                              | service `timeout`        |

`swagger` is optional and adds one public `GET` route for the service's OpenAPI
document (see [API docs](#api-docs)).

## Routes

56 API routes, plus 5 API document routes. Public paths equal the backend paths, except recommendation-service,
which has no `/api` prefix of its own.

| Service                | Public routes                                        | Gateway auth                                                    |
| ---------------------- | ---------------------------------------------------- | --------------------------------------------------------------- |
| auth-service           | `POST /api/auth/login`, `POST /api/auth/logout`, `POST /api/account` | public; login and register are rate limited per IP |
|                        | `GET/PUT/DELETE /api/account`, `PUT /api/account/password` | `user`, `admin`                                           |
| order-service          | `/api/order/cart`, `/api/order/checkout[/{id}]`, `/api/order/payment[/{id}]` | `user`, `admin`                     |
| product-service        | `/api/category/**`, `/api/product/**`, `/api/supplier/**` | `admin`; `GET /api/product/{id}` also `user`               |
| inventory-service      | `/api/po/**`, `/api/pr/**`                           | `admin`                                                         |
| recommendation-service | `GET /api/recommendations/users/{user_id}`, `GET /api/recommendations/{product_id}` → `/recommendations/...` | `user`, `admin` |
|                        | `GET /api/recommendations/mba/status`                | `admin`                                                         |
| —                      | `GET /__health`                                      | KrakenD's own liveness endpoint                                 |

The exact list is in `configuration/settings/*.json`. Not published on purpose:
product-service `/health`, the services' own Swagger UI pages, and
notification-service, which has no HTTP routes (it is a RabbitMQ consumer).

## API docs

With `env.swagger.enabled` set to `true`, each service's OpenAPI document is
published without authentication under `/docs/<service>/`:

| Gateway route                        | Service document                                  |
| ------------------------------------ | ------------------------------------------------- |
| `GET /docs/auth/openapi.json`        | auth-service `/q/openapi?format=json`             |
| `GET /docs/order/openapi.json`       | order-service `/api/v3/api-docs`                  |
| `GET /docs/product/openapi.json`     | product-service `/api/swagger/swagger.json` (Swagger 2.0) |
| `GET /docs/inventory/openapi.json`   | inventory-service `/api/v3/api-docs`              |
| `GET /docs/recommendation/openapi.json` | recommendation-service `/openapi.json`         |

The `/docs/<service>/` prefix is needed because order-service and
inventory-service serve their document on the same path. Set
`env.swagger.enabled` to `false` to remove all five routes at once, for example
on any deployment reachable from outside.

Only the JSON documents go through the gateway, not the Swagger UI pages. Each
UI loads its assets from a subtree (`/q/swagger-ui/*`, `/api/swagger-ui/*`,
`/api/swagger/*`), and KrakenD Community Edition has no wildcard routes to
proxy a subtree. To browse all services in one place, point any Swagger UI (a
`swaggerapi/swagger-ui` container with `URLS`, for example) at these five
documents. "Try it out" then calls the `servers` URL written in each document,
which is the service itself, not the gateway.

## Environment variables

| Variable                      | Overrides                         | Default                                                                 |
| ----------------------------- | --------------------------------- | ----------------------------------------------------------------------- |
| `AUTH_SERVICE_HOST`           | `auth_service.host`               | `http://host.docker.internal:7110`                                      |
| `ORDER_SERVICE_HOST`          | `order_service.host`              | `http://host.docker.internal:7120`                                      |
| `PRODUCT_SERVICE_HOST`        | `product_service.host`            | `http://host.docker.internal:7130`                                      |
| `INVENTORY_SERVICE_HOST`      | `inventory_service.host`          | `http://host.docker.internal:7140`                                      |
| `RECOMMENDATION_SERVICE_HOST` | `recommendation_service.host`     | `http://host.docker.internal:7170`                                      |
| `JWK_URL`                     | `env.jwt.jwk_url`                 | `http://host.docker.internal:8080/realms/ecommerce/protocol/openid-connect/certs` |
| `KEYCLOAK_ISSUER_URI`         | `env.jwt.issuer`                  | `http://localhost:8080/realms/ecommerce`                                |

The template is rendered on every start, so none of these need a rebuild.

## Running

Standalone, with the services running on the host:

```shell script
docker build -t polygot/gateway-service services/gateway-service
docker run --rm -p 7100:7100 \
  --add-host host.docker.internal:host-gateway \
  polygot/gateway-service
```

Once the services run on the `ecommerce` compose network, point the gateway at
them by name, e.g. `ORDER_SERVICE_HOST=http://order-service:7120` and
`JWK_URL=http://keycloak:8080/realms/ecommerce/protocol/openid-connect/certs`.

## Smoke test

```shell script
curl -i http://localhost:7100/__health

# login through the gateway
curl -i -X POST http://localhost:7100/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"userapp","password":"P@ssw0rd"}'

# no token -> 401 from the gateway, the service is never called
curl -i -X POST http://localhost:7100/api/order/cart

# "user" token on an admin-only route -> 403 from the gateway
curl -i http://localhost:7100/api/product -H "Authorization: Bearer $ACCESS_TOKEN"
```

## Configuration notes

**Encoding.** Every endpoint uses `no-op` on both the endpoint and the backend.
With KrakenD's default `json` encoding any non-2xx answer would become an
opaque 500 and the service's `ErrorResponse` body would be lost.

**Issuer.** Keycloak (`start-dev`, no fixed hostname) puts in `iss` the host the
token was requested through. The default `http://localhost:8080/realms/ecommerce`
matches the services' `KEYCLOAK_ISSUER_URI`. If tokens are minted through
another host name, set `KEYCLOAK_ISSUER_URI` to match. An empty
`env.jwt.issuer` in `env.json` skips the check (an empty environment variable
does not: it falls back to the default).

**Forwarded headers.** KrakenD forwards nothing that is not in `input_headers`.
Public routes get `partials/ih_default.tmpl`, which deliberately contains no
`Authorization` and no `X-User-*` headers, so a client cannot inject an
identity on a route where the gateway checks none. Protected routes get
`Authorization` plus `X-User-Id` (`sub`), `X-User-Name` (`preferred_username`)
and `X-User-Roles` (`realm_access.roles`), which the gateway sets from the
validated token. Only claims Keycloak always issues are propagated: for a
missing claim KrakenD would leave a client-sent header of that name in place.

**Rate limiting.** The `auth` profile in `env.json` (1 req/s, burst 5, per client
IP) protects login and registration in front of Keycloak's own brute force
detection. Behind another proxy, that proxy must set `X-Forwarded-For`.

**CORS / debug.** `allow_origins` is `*`, which is fine locally and wrong for
production; `allow_credentials` may only be enabled once `*` is gone.
`debug_endpoint` and `echo_endpoint` are off; `/__echo` reflects request
headers, including tokens.

## Adding the next service

1. Create `configuration/settings/<service>.json` with `host`, `host_env`, `timeout`,
   its `endpoints` and, if it serves an OpenAPI document, `swagger`.
2. Add `.<service>` to the `$services` list at the top of `configuration/krakend.tmpl`.

No new template is needed. Commas between endpoints are generated by the loop,
so a service with an empty `endpoints` list is harmless.
