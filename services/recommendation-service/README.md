# recommendation-service

Recommends products to a customer, or alongside a product, from what has been
checked out in `sales_order` and what sits in the cart. Built with FastAPI,
SQLAlchemy (async, asyncpg) and Redis.

The service owns no table. It reads the order-service data and keeps everything
it builds in Redis.

## Requirements

- Python 3.13
- PostgreSQL holding the `ecommerce` database (the one order-service writes
  `sales_order`, `sales_order_detail` and `product` to)
- Redis, the same instance order-service keeps the cart in
- Keycloak, the realm that issues the access tokens (`ecommerce`)

## Getting started

```shell script
cp .env-example .env     # then fill in DATABASE_URL, REDIS_*, PORT and KEYCLOAK_ISSUER_URI
make install             # creates venv/ and installs requirements.txt
make run                 # http://localhost:7170
```

`make dev` runs the same with auto reload, for working on the code. Run `make`
alone to list every target.

Without make:

```shell script
python3 -m venv venv
venv/bin/pip install -r requirements.txt
venv/bin/python main.py
```

## Configuration

Read from the environment, or from `.env` in this directory.

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

## Endpoints

| Method | Path | Role | What it does |
| --- | --- | --- | --- |
| `GET` | `/recommendations/users/{user_id}?limit=10` | `user` (own only) or `admin` | Recommendations for a customer (`user_id` is the UUID in `sales_order.created_by`) |
| `GET` | `/recommendations/{product_id}?limit=10` | `user` or `admin` | Recommendations to show alongside a product; `404` when the product is not sold |
| `GET` | `/recommendations/mba/status` | `admin` | When the association rules were last built, from how many orders; `404` before the first build |
| `GET` | `/` | — | Health check |

`limit` goes from 1 to 50.

## API documentation (Swagger)

FastAPI builds the OpenAPI document from the code and serves it with the
application. With the service running on its default port:

| What | Where |
| --- | --- |
| Swagger UI | <http://localhost:7170/docs> |
| ReDoc | <http://localhost:7170/redoc> |
| OpenAPI document (JSON) | <http://localhost:7170/openapi.json> |

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
and paste an access token to call the protected endpoints from there.
Swagger UI loads its scripts from a CDN, so it needs internet access.

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

To try it locally, take a token from Keycloak (or from `POST /api/auth/login` of
auth-service):

```shell script
TOKEN=$(curl -s http://localhost:8080/realms/ecommerce/protocol/openid-connect/token \
  -d grant_type=password -d client_id=ecommerce-app \
  -d username=userapp --data-urlencode 'password=P@ssw0rd' | jq -r .access_token)

curl -H "Authorization: Bearer $TOKEN" http://localhost:7170/recommendations/1
```

## Troubleshooting

**`redis.exceptions.AuthenticationError` or `NOAUTH` in the log.** The Redis of
`app/docker-compose.yml` requires a user. Set `REDIS_USERNAME` and
`REDIS_PASSWORD`; the service still starts without them, but every request and
the MBA job fail.

**A password holding `@`, `:` or `/` in `DATABASE_URL`.** It has to be
percent-encoded, `@` as `%40`, or the URL is split at the wrong `@`: with
`postgres:p@ssw0rd@localhost` the password read is `p` and the host
`ssw0rd@localhost`. The Redis password has its own variable for that reason.

## How a recommendation is built

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

## Running the tests

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
`*_test.py` and mirror the source tree.

## Project layout

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
```
