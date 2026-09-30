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

## Getting started

```shell script
cp .env-example .env     # then fill in DATABASE_URL, REDIS_URL and PORT
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
| `REDIS_URL` | — | `redis://[user:password@]host:6379/0` — the cart's Redis |
| `PORT` | — | Port the service listens on, `7170` |
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

| Method | Path | What it does |
| --- | --- | --- |
| `GET` | `/recommendations/users/{user_id}?limit=10` | Recommendations for a customer (`user_id` is the UUID in `sales_order.created_by`) |
| `GET` | `/recommendations/{product_id}?limit=10` | Recommendations to show alongside a product; `404` when the product is not sold |
| `GET` | `/recommendations/mba/status` | When the association rules were last built, from how many orders; `404` before the first build |
| `GET` | `/` | Health check |

`limit` goes from 1 to 50. The interactive documentation is at
<http://localhost:7170/docs>.

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
core/config.py               settings
db/database.py               PostgreSQL session and Redis client
repository/history.py        SQL over sales_order, and the cart in Redis
repository/rules.py          association rules in Redis
service/recommendation.py    the four steps of a recommendation
service/fpgrowth.py          FP-Growth and association rules
service/mba.py               the Market Basket Analysis job
main.py                      application and its lifespan
tests/                       unit tests, one *_test.py per module
```
