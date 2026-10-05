# recommendation-service

Product recommendations for the polygot-ecommerce platform: what to show a
customer, or alongside a product, mined from the sales orders and the cart with
Market Basket Analysis (FP-Growth) and three fallback strategies.

## Table of Contents

- [Overview](#overview)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Project Flow](#project-flow)
  - [Recommendations for a customer](#recommendations-for-a-customer)
  - [Recommendations for a product](#recommendations-for-a-product)
  - [The four-step fill](#the-four-step-fill)
  - [Market Basket Analysis rebuild](#market-basket-analysis-rebuild)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Configuration](#configuration)
  - [Clean](#clean)
  - [Install](#install)
  - [Run](#run)
- [API Documentation (Swagger)](#api-documentation-swagger)
- [Authentication](#authentication)
- [Endpoints](#endpoints)
- [Testing](#testing)
- [Project Layout](#project-layout)
- [Design Notes](#design-notes)
  - [How a recommendation is built](#how-a-recommendation-is-built)
  - [Market Basket Analysis](#market-basket-analysis)
  - [What is kept in Redis](#what-is-kept-in-redis)
- [Troubleshooting](#troubleshooting)

## Overview

Recommends products to a customer, or alongside a product, from what has been
checked out in `sales_order` and what sits in the cart. Built with FastAPI,
SQLAlchemy (async, asyncpg) and Redis, on port **7170**.

The service owns no table. It reads the data order-service writes
(`sales_order`, `sales_order_detail`, `product` in the `ecommerce` database,
whose schema is managed centrally by Liquibase in `/migrations`) and the cart
order-service keeps in Redis, and keeps everything it builds in Redis.

Behind the KrakenD gateway (gateway-service, port 7100) it is reached as
`GET /api/recommendations/...`, which the gateway forwards to
`/recommendations/...` here.

## Tech Stack

| Technology | Version | Why we use it |
| --- | --- | --- |
| Python | 3.13 (`python:3.13-slim` image) | Readable language for the data-mining part (FP-Growth, rule scoring), with first-class `asyncio` for the I/O-bound request path |
| FastAPI | 0.142.1 | Async endpoints, dependency injection for the DB session, Redis client and token check (`Depends(authenticate)`), and an OpenAPI document generated from the code |
| Uvicorn | 0.54.0 | ASGI server; `main.py` starts it on `PORT`, `make dev` runs it with `--reload` |
| SQLAlchemy (asyncio) + asyncpg + greenlet | 2.1.1 / 0.31.0 / 3.5.6 | Non-blocking PostgreSQL access; the queries are plain SQL `text()` over the order-service tables, since this service owns no model |
| redis (redis-py, asyncio) | 8.1.0 | Reads the order-service cart, caches recommendations, stores the association rules, and holds the rebuild lock (`SET NX` + a Lua release script) |
| PyJWT[crypto] | 2.15.1 | Verifies Keycloak RS256 access tokens; `PyJWKClient` fetches and caches the realm keys from the JWKS endpoint |
| pydantic-settings / python-dotenv | 2.15.0 / 1.2.3 | Typed settings read from the environment or `.env` (`core/config.py`) |
| Pydantic (via FastAPI) | — | Response models in `schemas/recommendation.py`: every response is validated and documented from them |
| FP-Growth (own implementation) | `service/fpgrowth.py` | Frequent itemsets and association rules (support, confidence, lift) in pure Python with no extra dependency; it works on weighted transactions and caps the itemset size (`MBA_MAX_ITEMSET_SIZE`). The repository does not state why a library such as mlxtend was not used; a likely reason is avoiding pandas/numpy in the image |
| pytest + pytest-asyncio + pytest-cov | 9.1.1 / 1.4.0 / 7.1.0 | Unit tests (async mode `auto`) with a 90% coverage gate |
| fakeredis[lua] | 2.38.0 | In-memory Redis for the tests, with Lua so the lock release script runs |
| httpx2 | 2.13.1 | HTTP client behind FastAPI's `TestClient` in the API tests |
| Docker | `python:3.13-slim` | Image runs as an unprivileged user (uid 10001), no `.env` baked in, configuration injected by compose |

## Architecture

```mermaid
flowchart LR
    client["Client<br/>browser or app"]
    gw["gateway-service<br/>KrakenD :7100"]
    kc["Keycloak 26.7<br/>realm ecommerce"]

    subgraph svc["recommendation-service :7170"]
        api["FastAPI<br/>api/recommendation.py"]
        job["MBA background job<br/>service/mba.py"]
    end

    subgraph data["Shared data"]
        pg[("PostgreSQL 16<br/>ecommerce db<br/>sales_order, sales_order_detail, product")]
        redis[("Redis 7<br/>cart, cache, MBA rules")]
    end

    order["order-service<br/>Spring Boot :7120"]

    client -->|"GET /api/recommendations/..."| gw
    gw -->|"GET /recommendations/..."| api
    api -.->|"JWKS, cached"| kc
    api -->|"read only SQL"| pg
    api -->|"read cart, read and write cache, read rules"| redis
    job -->|"read baskets"| pg
    job -->|"write rules, lock, clear cache"| redis
    order -->|"writes orders"| pg
    order -->|"writes cart"| redis

    classDef ext fill:#eef,stroke:#557
    classDef own fill:#efe,stroke:#575
    classDef store fill:#ffe,stroke:#995
    class client,gw,kc,order ext
    class api,job own
    class pg,redis store
```

Inside the service the code is layered: the router only handles HTTP, auth and
caching; the service layer decides what to recommend; the repository layer
holds the SQL and the Redis key formats; `db/` provides the connections.

```mermaid
flowchart TB
    subgraph http["HTTP layer"]
        main["main.py<br/>app, lifespan, error handler"]
        router["api/recommendation.py<br/>routes and response cache"]
        sec["core/security.py<br/>Keycloak token check"]
        schemas["schemas/recommendation.py<br/>response models"]
    end

    subgraph logic["Service layer"]
        rec["service/recommendation.py<br/>four-step fill"]
        mba["service/mba.py<br/>rebuild job"]
        fp["service/fpgrowth.py<br/>FP-Growth and rules"]
    end

    subgraph repo["Repository layer"]
        hist["repository/history.py<br/>SQL and cart keys"]
        rules["repository/rules.py<br/>mba keys"]
    end

    subgraph infra["db and config"]
        db["db/database.py<br/>async engine, Redis client"]
        cfg["core/config.py<br/>settings"]
    end

    main --> router
    main -->|"starts on lifespan"| mba
    router --> sec
    router --> schemas
    router --> rec
    rec --> hist
    rec --> rules
    mba --> hist
    mba --> fp
    mba --> rules
    hist --> db
    rules --> db
    db --> cfg

    classDef layer fill:#f6f6f6,stroke:#888
    class main,router,sec,schemas,rec,mba,fp,hist,rules,db,cfg layer
```

## Project Flow

### Recommendations for a customer

`GET /recommendations/users/{user_id}`. The token is checked first; a `user`
may only ask for their own `user_id`, an `admin` for anyone's. The answer is
cached per customer and `limit` under `recom:user:<user_id>:<limit>`. On a
miss, the history (purchased products plus cart) is gathered and the list is
filled in four steps, then stored for `RECOMMENDATION_CACHE_TTL` seconds.
`source` in the response tells whether it came from `redis` or was built now
(`postgresql`).

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant G as Gateway :7100
    participant A as API router
    participant K as Keycloak JWKS
    participant R as Redis
    participant S as Recommendation service
    participant P as PostgreSQL

    C->>G: GET /api/recommendations/users/USER_ID?limit=10
    G->>A: GET /recommendations/users/USER_ID?limit=10
    A->>K: fetch signing key, cached by kid
    A->>A: verify RS256, issuer, exp, realm role
    alt sub differs from USER_ID and not admin
        A-->>C: 403 Forbidden
    end
    A->>R: GET recom:user:USER_ID:LIMIT
    alt cache hit
        R-->>A: cached JSON
        A-->>C: 200 source redis
    else cache miss
        A->>S: recommend_for_user
        S->>P: purchased products, any order status
        S->>R: SCAN cart:USER_ID:* then MGET
        S->>S: seed = purchased plus cart
        S->>R: MGET mba:rules for seed products
        S->>P: bought together, same category, popular as needed
        S->>P: product details, active and not deleted only
        S-->>A: history and recommended_items
        A->>R: SET recom:user:USER_ID:LIMIT EX 300
        A-->>C: 200 source postgresql
    end
```

### Recommendations for a product

`GET /recommendations/{product_id}`. Any `user` or `admin` may call it. The
history is the product alone. A product that does not exist or is no longer
sold answers `404`. The result is cached under `recom:product:<id>:<limit>` for
`PRODUCT_RECOMMENDATION_CACHE_TTL` seconds.

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant A as API router
    participant R as Redis
    participant S as Recommendation service
    participant P as PostgreSQL

    C->>A: GET /recommendations/PRODUCT_ID?limit=10 with Bearer token
    A->>R: GET recom:product:PRODUCT_ID:LIMIT
    alt cache hit
        A-->>C: 200 source redis
    else cache miss
        A->>S: recommend_for_product
        S->>P: product details for PRODUCT_ID
        alt not found or not sold
            S-->>A: None
            A-->>C: 404 Product not found
        else sold
            S->>S: four-step fill with seed = PRODUCT_ID
            S-->>A: product_id and recommended_items
            A->>R: SET recom:product:PRODUCT_ID:LIMIT EX 3600
            A-->>C: 200 source postgresql
        end
    end
```

### The four-step fill

Each step only runs while places are left, and excludes the seed plus what the
earlier steps already picked. With an empty history (a new customer) only
`popular` runs.

```mermaid
flowchart TD
    start(["seed = history product ids"]) --> empty{"seed empty?"}
    empty -->|"no"| s1["1. frequently_bought_together<br/>MBA rules whose whole antecedent is in the seed<br/>score = best rule confidence"]
    s1 --> s2["2. bought_together<br/>what other customers with the same products bought<br/>score = number of those customers"]
    s2 --> s3["3. same_category<br/>best sellers of the seed categories<br/>score = quantity sold"]
    s3 --> s4["4. popular<br/>best sellers overall<br/>score = quantity sold"]
    empty -->|"yes"| s4
    s4 --> det["Load product details<br/>drop products no longer sold"]
    det --> out(["recommended_items, at most limit"])

    classDef step fill:#eef,stroke:#557
    class s1,s2,s3,s4 step
```

### Market Basket Analysis rebuild

The job starts with the service (when `MBA_ENABLED`) and repeats every
`MBA_INTERVAL` seconds; `make mba` runs it once by hand. It takes a Redis lock
so only one instance rebuilds at a time, mines the baskets with FP-Growth in a
worker thread, replaces the rules, and clears every cached recommendation built
from the old rules. A failed run is logged and keeps the previous rules.

```mermaid
sequenceDiagram
    autonumber
    participant L as Lifespan loop
    participant J as refresh_rules
    participant R as Redis
    participant P as PostgreSQL
    participant F as FP-Growth thread

    loop every MBA_INTERVAL seconds
        L->>J: run with a new DB session
        J->>R: SET mba:lock TOKEN NX EX max of MBA_INTERVAL and 60
        alt lock held by another instance
            R-->>J: nil
            J-->>L: skipped, None
        else lock acquired
            J->>P: one basket per sales order, any status
            J->>F: frequent_itemsets then association_rules
            F-->>J: rules filtered by confidence and lift
            J->>R: pipeline - delete stale mba:rules keys, SET mba:rules per product, SADD mba:products, SET mba:meta
            J->>R: SCAN recom:* then DEL
            J->>R: EVAL release script, deletes mba:lock only if TOKEN matches
            J-->>L: meta
        end
        opt run fails
            J-->>L: exception logged, previous rules kept
        end
    end
```

## Getting Started

### Prerequisites

- Python 3.13
- PostgreSQL holding the `ecommerce` database (the one order-service writes
  `sales_order`, `sales_order_detail` and `product` to)
- Redis, the same instance order-service keeps the cart in
- Keycloak, the realm that issues the access tokens (`ecommerce`)
- Docker with Compose, to run it in a container or as part of the stack

### Configuration

Read from the environment, or from `.env` in this directory
(`cp .env-example .env`).

| Variable | Default | What it is |
| --- | --- | --- |
| `DATABASE_URL` | — | `postgresql+asyncpg://user:password@host:5432/ecommerce` |
| `REDIS_URL` | — | `redis://host:6379/0` — the cart's Redis |
| `REDIS_USERNAME` | — | Redis user, `admin` with `app/docker-compose.yml` |
| `REDIS_PASSWORD` | — | Its password, taken as it is: no escaping needed |
| `PORT` | — | Port the service listens on, `7170` |
| `KEYCLOAK_ISSUER_URI` | `http://localhost:8080/realms/ecommerce` | The realm the access tokens must come from |
| `CART_KEY_PREFIX` | `cart` | Must match `redis.cart.key-prefix` of order-service |
| `RECOMMENDATION_LIMIT` | `10` | Items answered when the request names no `limit` |
| `RECOMMENDATION_CACHE_TTL` | `300` | Seconds a customer's recommendation is cached |
| `PRODUCT_RECOMMENDATION_CACHE_TTL` | `3600` | Seconds a product's recommendation is cached |
| `MBA_ENABLED` | `true` | Runs the Market Basket Analysis job inside the service |
| `MBA_INTERVAL` | `3600` | Seconds between two rebuilds of the association rules |
| `MBA_MIN_SUPPORT` | `0.01` | Share of the orders a pattern must appear in |
| `MBA_MIN_ORDER_COUNT` | `2` | Orders a pattern must appear in, whatever the share |
| `MBA_MIN_CONFIDENCE` | `0.1` | Lowest confidence a rule may have |
| `MBA_MIN_LIFT` | `1.0` | A rule's lift must be above this (1 = independent products) |
| `MBA_MAX_ITEMSET_SIZE` | `3` | Most products a pattern may span |
| `MBA_MAX_RULES_PER_PRODUCT` | `50` | Rules kept per product, most confident first |

`DATABASE_URL`, `REDIS_URL` and `PORT` have no default: the service does not
start without them.

### Clean

```shell script
make clean
```

It removes `.pytest_cache`, `htmlcov`, `.coverage`, `coverage.xml` and every
`__pycache__` outside the virtualenv. It keeps `venv/`. To start over
completely, without make:

```shell script
rm -rf venv .pytest_cache htmlcov .coverage coverage.xml
find . -name __pycache__ -not -path "./venv/*" -exec rm -rf {} +
```

### Install

```shell script
cp .env-example .env     # then fill in DATABASE_URL, REDIS_*, PORT and KEYCLOAK_ISSUER_URI
make install             # creates venv/ and installs requirements.txt
make install-dev         # same, plus the test dependencies of requirements-dev.txt
```

Without make:

```shell script
python3 -m venv venv
venv/bin/pip install -r requirements.txt        # or requirements-dev.txt for the tests
```

Run `make` alone to list every target (`help`, `install`, `install-dev`, `run`,
`dev`, `test`, `test-coverage`, `mba`, `clean`).

### Run

**Locally**

```shell script
make run                 # http://localhost:7170, port from .env
make dev                 # auto reload, for working on the code (PORT defaults to 7170)
make mba                 # rebuild the association rules once, then exit
```

Without make:

```shell script
venv/bin/python main.py
venv/bin/uvicorn main:app --reload --host 0.0.0.0 --port 7170
venv/bin/python -m service.mba
```

**With Docker** (the image reads no `.env`; pass the configuration as
environment):

```shell script
docker build -t polygot/recommendation-service:latest .
docker run --rm -p 7170:7170 --env-file .env polygot/recommendation-service:latest
```

Inside a container, `localhost` is the container itself: point
`DATABASE_URL`, `REDIS_URL` and `KEYCLOAK_ISSUER_URI` at
`host.docker.internal` or at the compose service names.

**As part of the stack** (from the repository root):

```shell script
./build.sh                                   # whole stack: infra up, migrate, provision Keycloak, services built and started
./build.sh recommendation-service            # rebuild and restart this service only
docker compose -f app/docker-compose.yml up -d --build recommendation-service
./down.sh                                    # stop the stack
```

`./build.sh` with no argument starts the infrastructure first (PostgreSQL,
MinIO, Keycloak, Redis, RabbitMQ, Mailpit), migrates the `ecommerce` database
with Liquibase (`app/init/migrate.sh`), provisions the Keycloak realm
(`app/init/keycloak-init.sh`), then builds and starts every service. With a
service name it only brings that service up, rebuilding it, and skips the
migration and the Keycloak step. In compose the service gets `PORT=7170`, the
`postgres`, `redis` and `keycloak` hosts, `REDIS_USERNAME` `admin`, and waits
for those three to be healthy; its healthcheck calls `GET /`.

## API Documentation (Swagger)

FastAPI builds the OpenAPI document from the code and serves it with the
application. With the service running on its default port:

| What | Where |
| --- | --- |
| Swagger UI | <http://localhost:7170/docs> |
| ReDoc | <http://localhost:7170/redoc> |
| OpenAPI document (JSON) | <http://localhost:7170/openapi.json> |
| OpenAPI document through the gateway | <http://localhost:7100/docs/recommendation/openapi.json> |

The gateway only publishes the JSON document (backend `/openapi.json`); Swagger
UI and ReDoc are served by the service itself.

The document is assembled from:

- `main.py` — title, version, description and the tags;
- the `summary`, `description` and `responses` on each route of
  `api/recommendation.py`, and the `Path` / `Query` descriptions of their
  parameters;
- the Pydantic models of `schemas/recommendation.py` — the shape of every
  response, the meaning of each field, and the example Swagger UI shows.

Every response is validated against those models (`response_model`), so the
documentation cannot drift from what the service really sends. When an endpoint
answers something new, change its model first, or it answers `500`.

The document declares the `bearerAuth` scheme: press **Authorize** in Swagger UI
and paste an access token (the token alone, without `Bearer`) to call the
protected endpoints from there. Swagger UI loads its scripts from a CDN, so it
needs internet access.

To get a token, take one from Keycloak (or from `POST /api/auth/login` of
auth-service):

```shell script
TOKEN=$(curl -s http://localhost:8080/realms/ecommerce/protocol/openid-connect/token \
  -d grant_type=password -d client_id=ecommerce-app \
  -d username=userapp --data-urlencode 'password=P@ssw0rd' | jq -r .access_token)

curl -H "Authorization: Bearer $TOKEN" http://localhost:7170/recommendations/1
curl -H "Authorization: Bearer $TOKEN" http://localhost:7100/api/recommendations/1   # through the gateway
```

A customer recommendation looks like this:

```json
{
  "source": "postgresql",
  "data": {
    "user_id": "aaaaaaaa-0000-0000-0000-000000000000",
    "history": { "purchased_product_ids": [1], "cart_product_ids": [4] },
    "recommended_items": [
      {
        "product_id": 2, "score": 0.4, "reason": "frequently_bought_together",
        "name": "Mouse", "sell_price": 15.0, "image_url": null, "category_id": 1,
        "rule": { "antecedent": [1], "consequent": [2], "support": 0.25, "confidence": 0.4, "lift": 1.6 }
      },
      { "product_id": 3, "score": 3, "reason": "bought_together", "name": "Keyboard", "...": "..." }
    ]
  }
}
```

`source` says whether the answer was built now (`postgresql`) or served from the
cache (`redis`).

## Authentication

Every `/recommendations` endpoint needs a Keycloak access token, checked the way
order-service and inventory-service check theirs (`core/security.py`):

- sent as `Authorization: Bearer <token>`;
- an RS256 JWT signed by a key of the realm, fetched from
  `{KEYCLOAK_ISSUER_URI}/protocol/openid-connect/certs` and cached, so a key
  rotation needs no restart;
- issued by `KEYCLOAK_ISSUER_URI`, not expired, with a `sub` and an `exp`;
- holding the `user` or `admin` **realm** role (`realm_access.roles`).

A customer may only read their own recommendations — the `sub` of the token must
be the `user_id` of the path — because they reveal what that customer bought.
An admin may read anyone's. The health check and the documentation stay open.

A refused request answers the error shape of the other services:

| Status | When |
| --- | --- |
| `400 Bad Request` | The token is not a well-formed JWT, or its `sub` is not a user id |
| `401 Unauthorized` | No token, or one that is expired, from another realm, or not signed by the realm |
| `403 Forbidden` | The token lacks the role, or asks for another customer's recommendations |
| `500 Internal Server Error` | Keycloak could not be reached for its keys |

```json
{
  "code": 401,
  "status": "Unauthorized",
  "data": { "timestamp": "2026-09-30T16:05:15.909929", "status": 401, "error": "No access token found, please login first" }
}
```

## Endpoints

| Method | Path | Gateway path | Role | What it does |
| --- | --- | --- | --- | --- |
| `GET` | `/recommendations/users/{user_id}?limit=10` | `/api/recommendations/users/{user_id}` | `user` (own only) or `admin` | Recommendations for a customer (`user_id` is the UUID in `sales_order.created_by`) |
| `GET` | `/recommendations/{product_id}?limit=10` | `/api/recommendations/{product_id}` | `user` or `admin` | Recommendations to show alongside a product; `404` when the product is not sold |
| `GET` | `/recommendations/mba/status` | `/api/recommendations/mba/status` | `admin` | When the association rules were last built, from how many orders; `404` before the first build |
| `GET` | `/` | — | — | Health check |
| `GET` | `/docs`, `/redoc`, `/openapi.json` | `/docs/recommendation/openapi.json` (JSON only) | — | API documentation |

`limit` goes from 1 to 50 and defaults to `RECOMMENDATION_LIMIT`.

## Testing

```shell script
make install-dev     # once: adds the test dependencies of requirements-dev.txt
make test            # like `yarn test`
make test-coverage   # like `yarn test:coverage`
```

`make test-coverage` prints the coverage per file, writes an HTML report to
`htmlcov/index.html`, and **fails below 90%**. The threshold is `fail_under` in
`.coveragerc`.

Without make:

```shell script
venv/bin/python -m pytest
venv/bin/python -m pytest --cov --cov-report=term-missing --cov-report=html
venv/bin/python -m pytest tests/service/fpgrowth_test.py     # one file
venv/bin/python -m pytest -k frequently_bought_together      # tests by name
```

The tests need neither PostgreSQL nor Redis: Redis is replaced by `fakeredis`
and the database by `FakeSession` (`tests/helpers.py`), and `tests/conftest.py`
sets the environment so a local `.env` is never used. Test files are named
`*_test.py` and mirror the source tree (`pytest.ini`: `python_files = *_test.py`,
`asyncio_mode = auto`).

## Project Layout

```
api/recommendation.py        endpoints and the recommendation cache
schemas/recommendation.py    response models, also what Swagger UI shows
core/config.py               settings
core/security.py             Keycloak access token check
db/database.py               PostgreSQL session and Redis client
repository/history.py        SQL over sales_order, and the cart in Redis
repository/rules.py          association rules in Redis
service/recommendation.py    the four steps of a recommendation
service/fpgrowth.py          FP-Growth and association rules
service/mba.py               the Market Basket Analysis job
main.py                      application and its lifespan
tests/                       unit tests, one *_test.py per module
Dockerfile                   python:3.13-slim image, runs as a non-root user
Makefile                     install, run, dev, test, test-coverage, mba, clean
.env-example                 configuration template
```

## Design Notes

### How a recommendation is built

The history is every product in the customer's sales orders, **whatever their
status** — a cancelled or expired order still says what the customer wanted —
plus every product in their cart. For a product page, the history is that
product alone.

The list is then filled in four steps, each one only while places are left.
Products in the history, and products no longer sold, are never recommended.

| Step | `reason` | Method | `score` |
| --- | --- | --- | --- |
| 1 | `frequently_bought_together` | Market Basket Analysis: association rules whose whole antecedent is in the history | Confidence of the best rule |
| 2 | `bought_together` | User-based collaborative filtering: what other customers who bought the same products also bought | Number of those customers |
| 3 | `same_category` | Content based: best sellers of the same categories | Quantity sold |
| 4 | `popular` | Best sellers overall, so a new customer still gets a list | Quantity sold |

### Market Basket Analysis

Each sales order is one basket. A background job mines the baskets with
**FP-Growth** (`service/fpgrowth.py`) and turns the frequent itemsets into
association rules such as `{Keyboard, Celana} -> {Topi}`, each measured by:

- **support** — share of all orders holding every product of the rule;
- **confidence** — of the orders holding the antecedent, the share that also
  hold the consequent;
- **lift** — confidence divided by how common the consequent is anyway; above 1
  means the products really go together.

An itemset is frequent when it appears in at least
`max(MBA_MIN_ORDER_COUNT, ceil(MBA_MIN_SUPPORT × orders))` orders.

The job runs when the service starts and then every `MBA_INTERVAL` seconds. It
holds a Redis lock while it runs, so several instances of the service never
rebuild at the same time, and it clears the cached recommendations once the new
rules are in. To rebuild by hand:

```shell script
make mba
```

All the baskets are read into memory on every run. Raise `MBA_MIN_SUPPORT` as
the orders grow, or the rules multiply.

### What is kept in Redis

| Key | Written by | Holds |
| --- | --- | --- |
| `cart:{user_id}:{product_id}` | order-service | Quantity of a product in the cart (read only here) |
| `recom:user:{user_id}:{limit}` | this service | A customer's recommendation, for `RECOMMENDATION_CACHE_TTL` |
| `recom:product:{product_id}:{limit}` | this service | A product's recommendation, for `PRODUCT_RECOMMENDATION_CACHE_TTL` |
| `mba:rules:{product_id}` | MBA job | The rules whose antecedent includes the product |
| `mba:products` | MBA job | The products that have rules |
| `mba:meta` | MBA job | How and when the rules were built |
| `mba:lock` | MBA job | Held while a rebuild runs |

A cached recommendation can lag a checkout or a cart change by up to its TTL.

## Troubleshooting

**`redis.exceptions.AuthenticationError` or `NOAUTH` in the log.** The Redis of
`app/docker-compose.yml` requires a user. Set `REDIS_USERNAME` and
`REDIS_PASSWORD`; the service still starts without them, but every request and
the MBA job fail.

**A password holding `@`, `:` or `/` in `DATABASE_URL`.** It has to be
percent-encoded, `@` as `%40`, or the URL is split at the wrong `@`: with
`postgres:p@ssw0rd@localhost` the password read is `p` and the host
`ssw0rd@localhost`. The Redis password has its own variable for that reason.

**`GET /recommendations/mba/status` answers `404`.** The rules have not been
built yet: the job has not finished its first run, `MBA_ENABLED` is `false`, or
another instance holds `mba:lock`. Run `make mba` to build them once.
