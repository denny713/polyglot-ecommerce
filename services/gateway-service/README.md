# gateway-service

The single front door of polygot-ecommerce, built on [KrakenD](https://www.krakend.io/)
2.x. It is stateless and declarative: there is no code here, only the
configuration that maps public routes onto backend services.

Right now it publishes the two endpoints of `auth-service`. Everything else —
CORS, rate limiting, timeouts — is applied here so the services behind it do
not each have to solve it again.

## Routes

| Public route (gateway)  | Backend                          | Notes                                    |
| ----------------------- | -------------------------------- | ---------------------------------------- |
| `POST /api/auth/login`  | `auth-service POST /api/auth/login`  | rate limited per client IP           |
| `POST /api/auth/logout` | `auth-service POST /api/auth/logout` | answers `204 No Content`             |
| `GET /__health`         | —                                | KrakenD's own liveness endpoint, built in |

Status codes and bodies are **passed through unchanged**. `401 INVALID_CREDENTIALS`,
`403 ACCOUNT_DISABLED`, `429 ACCOUNT_LOCKED` and `503` from auth-service arrive
at the client exactly as auth-service produced them, `ErrorResponse` body
included — see the comment at the top of `config/templates/auth_service.tmpl`
for why that needs `no-op` encoding rather than KrakenD's default.

## Layout

```
config/
├── krakend.tmpl              # root template: service-wide settings + endpoint list
├── settings/                 # the values, one file per concern
│   ├── service.json          # port, timeouts, CORS
│   └── auth_service.json     # backend host, forwarded headers, rate limit
└── templates/
    └── auth_service.tmpl     # the endpoint definitions for auth-service
```

This is KrakenD's [flexible configuration](https://www.krakend.io/docs/configuration/flexible-config/):
the binary renders `krakend.tmpl` as a Go template at startup and parses the
result. Each file in `settings/` becomes a top-level variable named after the
file, so `settings/service.json` is reachable as `{{ .service }}`.

The rendered configuration is written to `/tmp/krakend.json` inside the
container (`FC_OUT`), which is the first thing to look at when a change does
not do what it reads like:

```shell script
docker exec gateway cat /tmp/krakend.json
```

## Running

The gateway is part of the stack, so `./build.sh` from the repository root
brings it up along with PostgreSQL, MinIO and Keycloak. It is published on
**`http://localhost:8090`** (`GATEWAY_PORT` to change it — 8080 is Keycloak's).

To rebuild just this service after a configuration change:

```shell script
docker compose -p polygot-ecommerce -f app/docker-compose.yml up -d --build gateway
```

Standalone, without the rest of the stack:

```shell script
docker build -t polygot/gateway-service services/gateway-service
docker run --rm -p 8090:8080 \
  -e AUTH_SERVICE_HOST=http://host.docker.internal:8081 \
  --add-host host.docker.internal:host-gateway \
  polygot/gateway-service
```

### Where auth-service has to be

The gateway runs in a container and auth-service does not, so it reaches it
through the `host.docker.internal` alias. Start auth-service on **port 8081**,
because Keycloak already holds 8080 on the host:

```shell script
cd services/auth-service && QUARKUS_HTTP_PORT=8081 ./mvnw quarkus:dev
```

`AUTH_SERVICE_HOST` overrides the default from `settings/auth_service.json` and
is the only thing that has to change once auth-service is containerised on the
`polygot` network — set it to `http://auth-service:8080` in
`app/docker-compose.yml` (or in `.env`) and drop the `extra_hosts` entry. No
rebuild of the image is needed; the template is rendered on every start.

## Smoke test

```shell script
# login through the gateway
curl -i -X POST http://localhost:8090/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"userapp","password":"P@ssw0rd"}'

# wrong password -> 401 with auth-service's own error body, not a KrakenD 500
curl -i -X POST http://localhost:8090/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"userapp","password":"wrong"}'

# logout -> 204
curl -i -X POST http://localhost:8090/api/auth/logout \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}"

curl -i http://localhost:8090/__health
```

## Configuration notes

**Rate limiting.** `login_rate_limit` in `settings/auth_service.json` uses
KrakenD's `qos/ratelimit/router` keyed by client IP: one request per second
sustained, bursts of five (`client_capacity`). It is a cheap first filter in
front of the brute force detection Keycloak already does per account — it caps
how fast a single source can spray attempts, while Keycloak keeps counting
failures per user. Raise `client_max_rate` if a legitimate client trips it.
Note that behind another proxy the client IP is whatever reaches KrakenD, so
that proxy has to set `X-Forwarded-For` for the limit to key on the real
caller.

**CORS.** `settings/service.json` currently allows every origin, which is right
for local development and wrong for production. When you lock it down, list the
real origins in `allow_origins`; `allow_credentials` may only be turned on once
`"*"` is gone, since browsers reject a wildcard origin on a credentialed
request.

**Forwarded headers.** KrakenD forwards nothing to the backend unless it is
listed in `input_headers`. `Content-Type` is in the list for a concrete reason:
without it Quarkus answers `415 Unsupported Media Type`. Add `Authorization`
there when the first endpoint that needs a bearer token shows up.

## Adding the next service

Three steps, no changes to existing routes:

1. `settings/<service>.json` — host, timeouts, and whatever that service needs.
2. `templates/<service>.tmpl` — its endpoint objects, comma-separated (see
   `auth_service.tmpl`; the file emits the objects themselves, not the
   surrounding array).
3. One line in the `endpoints` array of `krakend.tmpl`:
   `{{ template "<service>.tmpl" . }}` — with a comma after the previous entry.

For a service that requires authentication, validate the token at the gateway
with the `auth/validator` extra_config pointed at the realm's JWKS
(`http://keycloak:8080/realms/ecommerce/protocol/openid-connect/certs`) instead
of letting each service do it. Keycloak already provisions a `gateway-service`
client for exactly that (see `app/init/keycloak-init.sh`).