# notification-service

The mail room of the polygot-ecommerce platform: a Node.js worker that consumes notification events from RabbitMQ and turns them into plain-text customer emails over SMTP. It has no public API; other services talk to it only through the `notification.exchange` exchange.

## Table of Contents

- [Overview](#overview)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Project Flow](#project-flow)
  - [Account events](#account-events)
  - [Order events](#order-events)
  - [Cart events and batching](#cart-events-and-batching)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Configuration](#configuration)
  - [Clean](#clean)
  - [Install](#install)
  - [Run](#run)
  - [Viewing emails in Mailpit](#viewing-emails-in-mailpit)
- [API Documentation (Swagger)](#api-documentation-swagger)
- [Message Contracts](#message-contracts)
- [Testing](#testing)
- [Project Layout](#project-layout)
- [Design Notes](#design-notes)
- [Troubleshooting](#troubleshooting)

## Overview

notification-service sends every email a customer gets from the platform. It does not decide when to send one. Other services publish an event when something happens, and this service works out who gets the email and what it says.

What it does:

- **Account emails** for events from `auth-service`: the temporary password after registration (`ACCOUNT_REGISTERED`), and security notices for `ACCOUNT_UPDATED`, `PASSWORD_CHANGED` and `ACCOUNT_DELETED`. These events already carry the recipient's address and name.
- **Order emails** for events from `order-service`: an unpaid order that expired (`CHECKOUT_EXPIRED`), a payment received (`PAYMENT_SUCCEEDED`) and a refund for a cancelled order (`REFUND_CANCELLATION`). These events carry only the id of a row. The service reads the order, payment or refund **read-only** from the shared `ecommerce` PostgreSQL database, and looks up the customer's email address and name in **Keycloak**.
- **Expired-cart emails** for `CART_EXPIRED` events from `order-service`. A Redis cart line expiry publishes one event per product, so the service collects a customer's events for a configurable window and then sends **one** email that lists every removed product.

Why it is asynchronous:

- **Decoupling.** Placing an order or registering an account never waits on an SMTP server, and an SMTP outage never fails a business transaction.
- **Durability.** Queues are durable and messages are acked only after the email is handed to SMTP. If the process crashes, RabbitMQ redelivers the unacked messages.
- **Batching.** A queue lets the service hold several cart events and merge them, which a synchronous call could not do.
- **One place for email.** Templates, SMTP settings and recipient lookup live in this one service, not in every producer.

The gateway (`gateway-service`, KrakenD on port 7100) does **not** route to this service. Port 7160 serves no API. It exists so the container has a port that the docker-compose TCP healthcheck can probe.

## Tech Stack

| Technology | Version | Why we use it |
|---|---|---|
| Node.js | 22 (`node:22-alpine` image) | The service mostly waits on I/O: RabbitMQ deliveries, PostgreSQL reads, Keycloak HTTP calls and SMTP. A single-threaded event loop handles that well, and Node 22's built-in `fetch` calls the Keycloak REST API with no extra HTTP client. |
| amqplib | 2.2.0 | A low-level AMQP 0-9-1 client that gives explicit control over `prefetch`, manual `ack`/`nack`, the `requeue` flag and `redelivered`. The retry rules and the cart batching (holding unacked messages for a window) depend on that control. |
| nodemailer | 10.0.12 | The standard SMTP client for Node. It handles implicit TLS (port 465), STARTTLS (`requireTLS`), optional auth and `verify()` at startup, so the same code works with Gmail-style relays and a plain local Mailpit. |
| Sequelize | 6.37.8 | Maps the `sales_order`, `sales_order_detail`, `payment`, `refund` and `product` tables that `order-service` owns, including associations and the soft-delete `defaultScope`. It is used read-only: `sync()` is never called because Liquibase owns the schema. |
| pg | 8.23.0 | The PostgreSQL driver Sequelize uses for the `postgres` dialect. |
| Keycloak Admin REST API (no SDK) | Keycloak 26.7 | Order and cart events identify the customer only by the user id in their token. The email address and name live in Keycloak, so the service gets a `client_credentials` token as the `notification-service` client (which has the `view-users` role) and calls `GET /admin/realms/{realm}/users/{id}`. Plain `fetch` keeps the dependency count low. |
| Express | 5.2.1 | Starts the HTTP listener on `PORT`. No routes are registered. The listener gives the container healthcheck a port to probe. |
| dotenv | 18.0.4 | Loads `.env` for local runs. The Docker image has no `.env`, so compose supplies the configuration. |
| Jest | 30.5.2 | Unit tests with module mocking for amqplib, nodemailer, Sequelize and `fetch`, fake timers for the cart batch window, and a coverage threshold enforced at 90%. |
| nodemon | 3.1.14 | Restarts the process on file changes during local development (`yarn dev`). |
| Docker | `node:22-alpine` | A small production image (`yarn install --production`, runs as the `node` user) that `app/docker-compose.yml` builds and runs next to RabbitMQ, PostgreSQL, Keycloak and Mailpit. |

## Architecture

### Platform context

```mermaid
flowchart LR
    subgraph Producers["Producers"]
        AUTH["auth-service<br/>Quarkus :7110"]
        ORDER["order-service<br/>Spring Boot :7120"]
    end

    subgraph Broker["RabbitMQ 4"]
        EX{{"notification.exchange<br/>direct, durable"}}
        QU["notification.push.user"]
        QT["notification.push.transaction"]
        QC["notification.push.cart"]
    end

    subgraph NS["notification-service :7160"]
        SVC["Node.js consumers"]
    end

    subgraph Lookups["Lookups"]
        KC["Keycloak<br/>realm ecommerce"]
        PG[("PostgreSQL<br/>ecommerce db")]
    end

    SMTP["SMTP server<br/>Mailpit :1025 locally"]
    INBOX["Customer inbox<br/>Mailpit UI :8025"]

    AUTH -- "notification.account" --> EX
    ORDER -- "notification.order" --> EX
    ORDER -- "notification.cart" --> EX
    EX --> QU
    EX --> QT
    EX --> QC
    QU --> SVC
    QT --> SVC
    QC --> SVC
    SVC -- "user lookup" --> KC
    SVC -- "read-only queries" --> PG
    SVC -- "sendMail" --> SMTP
    SMTP --> INBOX

    classDef producer fill:#e3f2fd,stroke:#1565c0,color:#0d47a1
    classDef broker fill:#fff3e0,stroke:#ef6c00,color:#e65100
    classDef service fill:#e8f5e9,stroke:#2e7d32,color:#1b5e20
    classDef store fill:#f3e5f5,stroke:#6a1b9a,color:#4a148c
    classDef mail fill:#fce4ec,stroke:#ad1457,color:#880e4f
    class AUTH,ORDER producer
    class EX,QU,QT,QC broker
    class SVC service
    class KC,PG store
    class SMTP,INBOX mail
```

### Internal modules

```mermaid
flowchart TB
    APP["src/app.js<br/>bootstrap"]

    subgraph Config["configuration"]
        DB["database.js<br/>Sequelize"]
        BR["broker.js<br/>amqplib channel"]
        ML["mailer.js<br/>nodemailer transport"]
    end

    subgraph Consumers["consumer"]
        EV["event.js<br/>account + order queues"]
        CA["cart.js<br/>batching consumer"]
        RT["retry.js<br/>shouldRetry"]
    end

    subgraph Handlers["handler"]
        HA["account.js"]
        HO["order.js<br/>dispatch by eventType"]
        HCK["checkout.js<br/>CHECKOUT_EXPIRED, PAYMENT_SUCCEEDED"]
        HRF["refund.js<br/>REFUND_CANCELLATION"]
        HCT["cart.js<br/>CART_EXPIRED"]
        HRC["recipient.js<br/>requireRecipient"]
        HER["error.js<br/>UnprocessableEventError"]
    end

    subgraph Data["repository and model"]
        REPO["repository/order.js"]
        MOD["model/*<br/>read-only Sequelize models"]
    end

    subgraph Services["service"]
        SACC["account.js<br/>Keycloak lookup"]
        SMAIL["mail.js<br/>sendEmail"]
    end

    TPL["template/*<br/>plain-text emails"]

    APP --> DB
    APP --> BR
    APP --> ML
    APP --> EV
    EV --> CA
    EV --> RT
    CA --> RT
    EV --> HA
    EV --> HO
    CA --> HCT
    HO --> HCK
    HO --> HRF
    HCK --> HRC
    HRF --> HRC
    HCT --> HRC
    HRC --> SACC
    HCK --> REPO
    HRF --> REPO
    HCT --> REPO
    REPO --> MOD
    MOD --> DB
    HA --> TPL
    HCK --> TPL
    HRF --> TPL
    HCT --> TPL
    HA --> SMAIL
    HCK --> SMAIL
    HRF --> SMAIL
    HCT --> SMAIL
    SMAIL --> ML
    RT -.-> HER

    classDef boot fill:#eceff1,stroke:#455a64,color:#263238
    classDef cfg fill:#fff3e0,stroke:#ef6c00,color:#e65100
    classDef cons fill:#e3f2fd,stroke:#1565c0,color:#0d47a1
    classDef hand fill:#e8f5e9,stroke:#2e7d32,color:#1b5e20
    classDef data fill:#f3e5f5,stroke:#6a1b9a,color:#4a148c
    classDef svc fill:#fce4ec,stroke:#ad1457,color:#880e4f
    class APP boot
    class DB,BR,ML cfg
    class EV,CA,RT cons
    class HA,HO,HCK,HRF,HCT,HRC,HER hand
    class REPO,MOD,TPL data
    class SACC,SMAIL svc
```

Startup order (`src/app.js`): connect to PostgreSQL (`sequelize.authenticate()`), connect to RabbitMQ, create the SMTP transport (`transporter.verify()` only logs the result), assert the exchange and the three queues and bind them, start the consumers, then listen on `PORT`. A failed database or broker connection exits the process with code 1. So does a broker connection that closes later. The container then relies on `restart: unless-stopped`.

## Project Flow

All three consumers share the same failure policy, implemented in `src/consumer/retry.js`:

| Failure | Example | Outcome |
|---|---|---|
| Malformed JSON (`SyntaxError`) | Body is not JSON | `nack` without requeue, so the message is dropped |
| `UnprocessableEventError` | Unknown `eventType`, missing recipient, order/payment/refund not found, user deleted in Keycloak or has no email | `nack` without requeue, so the message is dropped |
| Any other error, first delivery | SMTP down, Keycloak 5xx, database timeout | `nack` with requeue, so RabbitMQ redelivers it once |
| Any other error, already `redelivered` | The same failure again | `nack` without requeue, so the message is dropped |

No dead-letter exchange is configured on these queues, so a dropped message is discarded. The service logs the event type and id, never the body, because the registration event carries a password.

### Account events

Queue `notification.push.user`, routing key `notification.account`, prefetch 1. The event is flat and already contains `email`, `firstName` and `username`, so no lookup is needed. `handler/account.js` picks the template by `eventType` and sends the email.

```mermaid
sequenceDiagram
    autonumber
    participant AUTH as auth-service
    participant RMQ as RabbitMQ
    participant CON as consumer/event.js
    participant HND as handler/account.js
    participant TPL as template/account.js
    participant SMTP as SMTP / Mailpit

    AUTH->>RMQ: publish to notification.exchange, key notification.account
    RMQ->>CON: deliver from notification.push.user, prefetch 1
    CON->>CON: JSON.parse body
    CON->>HND: handleAccountEvent(event)
    alt unknown eventType or no email
        HND-->>CON: throw UnprocessableEventError
        CON->>RMQ: nack, requeue false, dropped
    else valid event
        HND->>TPL: build subject and text
        HND->>SMTP: sendMail to event.email
        alt SMTP accepted
            SMTP-->>HND: messageId
            CON->>RMQ: ack
        else SMTP error
            SMTP-->>HND: error
            opt first delivery
                CON->>RMQ: nack, requeue true
            end
            opt already redelivered
                CON->>RMQ: nack, requeue false, dropped
            end
        end
    end
```

`ACCOUNT_REGISTERED` is the only event `auth-service` publishes with publisher confirms and waits on. If the broker does not confirm it, registration is rolled back, because the email is the only copy of the generated password.

### Order events

Queue `notification.push.transaction`, routing key `notification.order`, prefetch 1. `order-service` publishes these after its database transaction commits, and they carry only an `id`. `handler/order.js` routes by `eventType`:

| eventType | `id` refers to | Data read | Email |
|---|---|---|---|
| `CHECKOUT_EXPIRED` | `sales_order.id` | Order with its lines and products, plus its refunds with reason `Expired` | "Your ecommerce order {doc} has expired", with any refunds listed |
| `PAYMENT_SUCCEEDED` | `payment.id` | Payment, then its sales order with lines | "Payment received ..." (subject and closing line depend on whether `outstanding` is 0) |
| `REFUND_CANCELLATION` | `refund.id` | Refund, then its sales order with lines | "Refund {doc} for your cancelled ecommerce order {doc}" |

The recipient is the order's `created_by` user, looked up in Keycloak through `handler/recipient.js`.

```mermaid
sequenceDiagram
    autonumber
    participant ORD as order-service
    participant RMQ as RabbitMQ
    participant CON as consumer/event.js
    participant HND as handler/order.js
    participant DB as PostgreSQL
    participant KC as Keycloak
    participant SMTP as SMTP / Mailpit

    ORD->>RMQ: after commit, publish key notification.order with eventType and id
    RMQ->>CON: deliver from notification.push.transaction
    CON->>HND: handleOrderEvent(event)
    alt unknown eventType or missing id
        HND-->>CON: UnprocessableEventError
        CON->>RMQ: nack, requeue false, dropped
    else known eventType
        HND->>DB: find payment, refund or sales order by id
        alt row not found or soft-deleted
            HND-->>CON: UnprocessableEventError
            CON->>RMQ: nack, requeue false, dropped
        else row found
            HND->>KC: client_credentials token, cached
            HND->>KC: GET admin users by order.createdBy
            opt 401 Unauthorized
                HND->>KC: refresh token and retry once
            end
            alt user 404 or no email
                HND-->>CON: UnprocessableEventError
                CON->>RMQ: nack, requeue false, dropped
            else user found
                HND->>SMTP: sendMail with rendered template
                CON->>RMQ: ack
            end
        end
    end
    opt transient error such as SMTP or Keycloak 5xx
        CON->>RMQ: nack, requeue only if not redelivered
    end
```

### Cart events and batching

Queue `notification.push.cart`, routing key `notification.cart`, prefetch `CART_PREFETCH` (default 100).

`order-service` keeps each cart line as a separate Redis key with a TTL. When a key expires, its `CartExpiryListener` publishes one `CART_EXPIRED` message with `userId` and `productId`. A customer who left five products in the cart would get five emails if each message were handled alone. `consumer/cart.js` avoids that:

1. The **first** message for a `userId` opens a batch and starts a timer of `CART_BATCH_WINDOW_MS` (default `300000`, five minutes).
2. Every later message for that user inside the window is added to the same batch. The message is kept **unacked**, and its `productId` goes into a `Set`, so duplicates collapse.
3. When the timer fires, the batch is flushed. `findProducts` loads the products that still exist (soft-deleted ones are skipped), the customer is looked up in Keycloak, and **one** `CART_EXPIRED` email lists every product at its current `sell_price`.
4. On success **every** message in the batch is acked. On failure each message is nacked, and is requeued only if it was not already a redelivery.

The messages stay unacked during the window, so a crash mid-window loses nothing because RabbitMQ redelivers them. Two limits follow from that design:

- `CART_PREFETCH` must be large enough to hold every message that can arrive inside one window. When it is reached, RabbitMQ stops delivering until a batch is flushed.
- `CART_BATCH_WINDOW_MS` must stay well below RabbitMQ's consumer ack timeout (30 minutes by default). Otherwise the broker closes the channel, and this service exits because the connection closed.

If none of the products exist any more, the batch is acked and no email is sent. A message with no `userId` or `productId`, or with a body that is not JSON, is dropped immediately and never joins a batch.

```mermaid
sequenceDiagram
    autonumber
    participant REDIS as Redis key expiry
    participant ORD as order-service
    participant RMQ as RabbitMQ
    participant CC as consumer/cart.js
    participant HC as handler/cart.js
    participant DB as PostgreSQL
    participant KC as Keycloak
    participant SMTP as SMTP / Mailpit

    REDIS->>ORD: cart line A expired
    ORD->>RMQ: CART_EXPIRED userId U, productId A
    RMQ->>CC: deliver message 1
    CC->>CC: new batch for U, start timer CART_BATCH_WINDOW_MS
    REDIS->>ORD: cart line B expired
    ORD->>RMQ: CART_EXPIRED userId U, productId B
    RMQ->>CC: deliver message 2
    CC->>CC: add to batch U, still unacked
    Note over CC: window elapses
    CC->>HC: handleCartExpired with userId U and productIds A, B
    HC->>DB: findProducts where id in A, B
    alt no product exists any more
        HC-->>CC: skipped, no email
        CC->>RMQ: ack messages 1 and 2
    else products found
        HC->>KC: GET admin users U
        alt user missing or no email
            HC-->>CC: UnprocessableEventError
            CC->>RMQ: nack all, requeue false
        else user found
            HC->>SMTP: one email listing A and B
            alt sent
                CC->>RMQ: ack messages 1 and 2
            else transient error
                CC->>RMQ: nack each, requeue if not redelivered
            end
        end
    end
```

## Getting Started

### Prerequisites

- **Node.js 22** and **Yarn 1.x** (the repo ships a `yarn.lock`). The Docker image uses `node:22-alpine`.
- **Docker** with the Compose plugin, to run the infrastructure (and optionally this service).
- A running stack of **RabbitMQ**, **PostgreSQL** with the migrated `ecommerce` database, **Keycloak** with the `ecommerce` realm and the `notification-service` client, and an **SMTP server**. Mailpit is the local one. `./build.sh` at the repo root provisions all of this.
- `make` (optional) for the Makefile shortcuts.

### Configuration

The service reads environment variables. For local runs, `dotenv` loads them from `services/notification-service/.env` (copy `.env-example`, where every key is present but empty). The "Default" column shows the value the **code** uses when the variable is unset. Where there is none, the variable is required. The "Compose value" column shows what `app/docker-compose.yml` sets.

| Variable | Default (code) | Compose value | Description |
|---|---|---|---|
| `PORT` | `7160` | `7160` | HTTP listener port (no routes, used by the healthcheck). |
| `BROKER_URL` | none, required | `amqp://admin:p%40ssw0rd@rabbitmq:5672` | AMQP connection URL. URL-encode special characters in the password. |
| `NOTIFICATION_EXCHANGE` | none, required | `notification.exchange` | Direct, durable exchange that is asserted at startup. |
| `ACCOUNT_QUEUE` | none, required | `notification.push.user` | Durable queue for account events. |
| `ACCOUNT_ROUTING_KEY` | none, required | `notification.account` | Binding key for `ACCOUNT_QUEUE`. |
| `ORDER_QUEUE` | none, required | `notification.push.transaction` | Durable queue for order events. |
| `ORDER_ROUTING_KEY` | none, required | `notification.order` | Binding key for `ORDER_QUEUE`. |
| `CART_QUEUE` | none, required | `notification.push.cart` | Durable queue for cart events. |
| `CART_ROUTING_KEY` | none, required | `notification.cart` | Binding key for `CART_QUEUE`. |
| `CART_BATCH_WINDOW_MS` | `300000` | `${CART_BATCH_WINDOW_MS:-300000}` | How long cart events for one user are collected before one email is sent. |
| `CART_PREFETCH` | `100` | `100` | Unacked-message limit for the cart consumer. It must hold every message that can arrive in one window. |
| `DB_HOST` | none (Sequelize falls back to `localhost`) | `postgres` | PostgreSQL host. |
| `DB_PORT` | `5432` | `5432` | PostgreSQL port. |
| `DB_USER` | none, required | `postgres` | Database user. |
| `DB_PASSWORD` | none, required | `p@ssw0rd` | Database password. |
| `DB_NAME` | none, required | `ecommerce` | Database that `order-service` writes and this service reads. |
| `KEYCLOAK_URL` | none, required | `http://keycloak:8080` | Keycloak base URL. A trailing `/` is stripped. |
| `KEYCLOAK_REALM` | none, required | `ecommerce` | Realm that holds the customers. |
| `KEYCLOAK_CLIENT_ID` | none, required | `notification-service` | Confidential client used for `client_credentials`. Its service account needs `view-users`. |
| `KEYCLOAK_CLIENT_SECRET` | none, required | `notification-service-secret` | Secret of that client. |
| `SMTP_HOST` | none, required | `mailpit` | SMTP server host. |
| `SMTP_PORT` | `587` | `1025` | SMTP port. `465` means implicit TLS. Any other port uses STARTTLS unless `SMTP_REQUIRE_TLS=false`. |
| `SMTP_REQUIRE_TLS` | STARTTLS required (any value other than `false`) | `false` | Set to `false` for a plain catcher such as Mailpit, which offers no TLS. Not listed in `.env-example`. |
| `SMTP_USER` | empty (no auth) | empty | SMTP username. Auth is sent only when this is set. |
| `SMTP_PASS` | empty | empty | SMTP password. |
| `MAIL_FROM` | falls back to `SMTP_USER` | `Ecommerce <no-reply@ecommerce.local>` | `From:` header of every email. |

A `.env` for running the service on the host against the compose infrastructure (default credentials, published ports):

```dotenv
PORT=7160

BROKER_URL=amqp://admin:p%40ssw0rd@localhost:5672
NOTIFICATION_EXCHANGE=notification.exchange
ACCOUNT_QUEUE=notification.push.user
ACCOUNT_ROUTING_KEY=notification.account
ORDER_QUEUE=notification.push.transaction
ORDER_ROUTING_KEY=notification.order
CART_QUEUE=notification.push.cart
CART_ROUTING_KEY=notification.cart
CART_BATCH_WINDOW_MS=10000
CART_PREFETCH=100

DB_HOST=localhost
DB_PORT=5432
DB_USER=postgres
DB_PASSWORD=p@ssw0rd
DB_NAME=ecommerce

KEYCLOAK_URL=http://localhost:8080
KEYCLOAK_REALM=ecommerce
KEYCLOAK_CLIENT_ID=notification-service
KEYCLOAK_CLIENT_SECRET=notification-service-secret

SMTP_HOST=localhost
SMTP_PORT=1025
SMTP_REQUIRE_TLS=false
SMTP_USER=
SMTP_PASS=
MAIL_FROM=Ecommerce <no-reply@ecommerce.local>
```

> Stop the `notification-service` container (`docker compose -f app/docker-compose.yml stop notification-service`) before running a local copy. Otherwise both processes consume the same queues and messages are split between them.

### Clean

The Makefile has no `clean` target. Remove the installed dependencies and the coverage report directly:

```bash
cd services/notification-service
rm -rf node_modules coverage
```

### Install

```bash
cd services/notification-service

make install        # runs: yarn install
# or natively
yarn install
```

### Run

#### Locally

```bash
cd services/notification-service

make run            # runs: yarn start
# or natively
yarn start          # node src/app.js
yarn dev            # nodemon src/app.js, restarts on file changes
```

On a successful start the log shows:

```text
Connected to database successfully
Connected to broker successfully
SMTP Server is ready to take our messages
Waiting for messages in queue: notification.push.user...
Waiting for messages in queue: notification.push.transaction...
Waiting for messages in queue: notification.push.cart (batched every 300000 ms)...
Notification Service is running on port 7160
```

(The SMTP line is logged asynchronously, so it can appear in a different position.)

#### With Docker (standalone image)

```bash
cd services/notification-service
docker build -t polygot/notification-service:latest .
docker run --rm --env-file .env -p 7160:7160 polygot/notification-service:latest
```

The image contains no `.env`, so pass the configuration with `--env-file` or `-e`. Inside a container, `localhost` refers to the container itself. Point `BROKER_URL`, `DB_HOST`, `KEYCLOAK_URL` and `SMTP_HOST` at reachable hosts, for example `host.docker.internal` on Docker Desktop, or join the `ecommerce` compose network.

#### As part of the stack

From the repository root:

```bash
# Whole platform: infra up, ecommerce DB migrated, Keycloak realm provisioned, every service built and started
./build.sh

# Rebuild and restart only this service (infra must already be up and provisioned)
./build.sh notification-service

# Or with Compose directly
docker compose -f app/docker-compose.yml up -d --build notification-service
docker compose -f app/docker-compose.yml logs -f notification-service
```

`./build.sh` runs in two phases. First it starts PostgreSQL, MinIO, Keycloak, Redis, RabbitMQ and Mailpit, migrates the `ecommerce` database (`app/init/migrate.sh`) and provisions Keycloak (`app/init/keycloak-init.sh`, which gives the `notification-service` client's service account the `view-users` role). Then it runs `docker compose up -d --build` for the services. In compose, the container waits for `postgres`, `rabbitmq`, `keycloak` and `mailpit` to be healthy. Its own healthcheck is a TCP connect to port 7160.

### Viewing emails in Mailpit

In the compose stack, `SMTP_HOST=mailpit` and `SMTP_PORT=1025`, so no email leaves your machine. Open **http://localhost:8025** to see every message the service sent. Try it by registering a user through `auth-service`, which sends an `ACCOUNT_REGISTERED` email with the temporary password, or by publishing a test message as shown below.

To send real email, point the `SMTP_*` variables at a real relay (for example Gmail on `587` with an app password) and leave `SMTP_REQUIRE_TLS` unset.

## API Documentation (Swagger)

**This service exposes no REST API, so it has no Swagger/OpenAPI document.** `src/app.js` creates an Express app but registers no routes. Any HTTP request to port 7160 gets Express's default `404`. The port exists only so the container healthcheck can open a TCP connection. `gateway-service` has no route to this service.

The service's interface is its message contracts. To inspect them:

- See [Message Contracts](#message-contracts) below for the exchange, routing keys, queues and payloads.
- Open the **RabbitMQ management UI** at **http://localhost:15672** (default `admin` / `p@ssw0rd`). Under *Queues and Streams* you can see `notification.push.user`, `notification.push.transaction` and `notification.push.cart`, with their ready and unacked counts and consumers. Under *Exchanges* you can see `notification.exchange` and its bindings.

### Publishing a test message

**From the management UI:** open *Exchanges*, select `notification.exchange`, then *Publish message*. Set the routing key to `notification.account`, add the property `content_type` = `application/json`, paste a payload from [Message Contracts](#message-contracts) and click *Publish message*. The email appears in Mailpit.

**From the command line** (management HTTP API, default vhost `/` = `%2F`):

```bash
curl -u 'admin:p@ssw0rd' -H 'content-type: application/json' \
  -X POST 'http://localhost:15672/api/exchanges/%2F/notification.exchange/publish' \
  -d '{
    "routing_key": "notification.account",
    "properties": { "content_type": "application/json" },
    "payload_encoding": "string",
    "payload": "{\"eventId\":\"test-1\",\"eventType\":\"PASSWORD_CHANGED\",\"occurredAt\":\"2026-10-05T08:00:00Z\",\"source\":\"manual-test\",\"accountId\":\"00000000-0000-0000-0000-000000000000\",\"username\":\"jane\",\"email\":\"jane@example.com\",\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"data\":{}}"
  }'
# => {"routed":true}
```

Account events are self-contained, so this works with any address. Order and cart events need real ids. The referenced `sales_order` / `payment` / `refund` / `product` row must exist and not be soft-deleted, and the customer id must exist in Keycloak. Otherwise the message is dropped with an `UnprocessableEventError` in the log.

## Message Contracts

All messages are JSON published to the **direct, durable** exchange `notification.exchange`. This service asserts the exchange, declares the durable queues and binds them at startup. The producers only publish to the exchange.

| Exchange | Routing key | Queue | Producer | Event types | Consumer prefetch |
|---|---|---|---|---|---|
| `notification.exchange` | `notification.account` | `notification.push.user` | auth-service | `ACCOUNT_REGISTERED`, `ACCOUNT_UPDATED`, `PASSWORD_CHANGED`, `ACCOUNT_DELETED` | 1 |
| `notification.exchange` | `notification.order` | `notification.push.transaction` | order-service | `CHECKOUT_EXPIRED`, `PAYMENT_SUCCEEDED`, `REFUND_CANCELLATION` | 1 |
| `notification.exchange` | `notification.cart` | `notification.push.cart` | order-service | `CART_EXPIRED` | `CART_PREFETCH` (100) |

Both producers also set the AMQP `message_id` to `eventId` and `type` to `eventType`. The consumer reads only the JSON body.

### Account event (auth-service `AccountEvent` record)

```json
{
  "eventId": "3f9b6c1e-2a7d-4c55-9e0b-7b1d2f8a4c10",
  "eventType": "ACCOUNT_REGISTERED",
  "occurredAt": "2026-10-05T08:00:00Z",
  "source": "auth-service",
  "accountId": "8d1c3b4e-5f60-4a7b-9c8d-0e1f2a3b4c5d",
  "username": "jane",
  "email": "jane@example.com",
  "firstName": "Jane",
  "lastName": "Doe",
  "data": { "temporaryPassword": "generated-password" }
}
```

| eventType | `data` | Email subject |
|---|---|---|
| `ACCOUNT_REGISTERED` | `{ "temporaryPassword": "..." }` | Your ecommerce account |
| `ACCOUNT_UPDATED` | `{ "changedFields": ["email", "firstName", "lastName"] }` (any subset) | Your ecommerce account was updated |
| `PASSWORD_CHANGED` | `{}` | Your ecommerce password was changed |
| `ACCOUNT_DELETED` | `{}` | Your ecommerce account was deleted |

Required by the consumer: a known `eventType` and a non-empty `email`.

### Order event (order-service `NotificationMsg`, null fields omitted)

```json
{
  "eventId": "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
  "eventType": "PAYMENT_SUCCEEDED",
  "occurredAt": "2026-10-05T08:00:00Z",
  "source": "order-service",
  "id": 42
}
```

`id` is a `sales_order.id` for `CHECKOUT_EXPIRED`, a `payment.id` for `PAYMENT_SUCCEEDED` and a `refund.id` for `REFUND_CANCELLATION`. Required by the consumer: a known `eventType` and a non-null `id`.

### Cart event (order-service `NotificationMsg`)

```json
{
  "eventId": "c0ffee00-1234-4abc-9def-001122334455",
  "eventType": "CART_EXPIRED",
  "occurredAt": "2026-10-05T08:00:00Z",
  "source": "order-service",
  "userId": "8d1c3b4e-5f60-4a7b-9c8d-0e1f2a3b4c5d",
  "productId": 7
}
```

Required by the consumer: `userId` (the Keycloak user id) and `productId`. `eventType` is not checked on this queue, because every message on it is treated as a cart expiry.

### Email formatting

Every email is **plain text**, so the temporary password cannot be reflowed or auto-linked by an HTML client. Order-side emails format amounts as IDR (`id-ID` locale) and times in `Asia/Jakarta` with a `WIB` suffix. Bank account numbers are masked to the last four digits (`****1234`).

## Testing

```bash
cd services/notification-service

make test           # runs: yarn test            (jest)
make coverage       # runs: yarn test:coverage   (jest --coverage)
# or natively
yarn test
yarn test:coverage
```

- 20 suites under `tests/` mirror `src/`: `configuration`, `consumer`, `handler`, `model`, `repository`, `service`, `template`. Shared order, payment and refund fixtures are in `tests/fixtures/order.js`.
- amqplib, nodemailer, Sequelize and `fetch` are mocked, so the tests need no RabbitMQ, PostgreSQL, Keycloak or SMTP server. The cart consumer tests use Jest fake timers to drive the batch window.
- Coverage is collected from `src/**/*.js`, excluding `src/app.js`, and the run **fails below 90%** for branches, functions, lines or statements. The current suite reaches 100% on all four.
- Reports are written to `coverage/`: open `coverage/lcov-report/index.html`, or use `lcov.info` / `clover.xml` in CI.

## Project Layout

```text
notification-service/
├── Dockerfile               # node:22-alpine production image, runs as user node, EXPOSE 7160
├── Makefile                 # install / run / test / coverage shortcuts around yarn
├── package.json             # dependencies, scripts and the Jest config with the 90% coverage threshold
├── yarn.lock                # locked dependency tree (installed with --frozen-lockfile in Docker)
├── .env-example             # every configuration key, empty
├── src/
│   ├── app.js               # bootstrap: DB, broker, mailer, consumers, HTTP listener
│   ├── configuration/
│   │   ├── broker.js        # amqplib connection and channel, exits on close
│   │   ├── database.js      # Sequelize instance for the ecommerce DB, read-only, never sync()
│   │   └── mailer.js        # nodemailer transport, TLS rules, verify() at startup
│   ├── consumer/
│   │   ├── event.js         # asserts the exchange, consumes the account and order queues with prefetch 1
│   │   ├── cart.js          # cart consumer: per-user batch window, ack or nack the whole batch
│   │   └── retry.js         # shouldRetry: requeue once, never for malformed or unprocessable events
│   ├── handler/
│   │   ├── account.js       # account events to templates, sent to event.email
│   │   ├── order.js         # dispatches order events by eventType
│   │   ├── checkout.js      # CHECKOUT_EXPIRED and PAYMENT_SUCCEEDED
│   │   ├── refund.js        # REFUND_CANCELLATION
│   │   ├── cart.js          # CART_EXPIRED for a batch of product ids
│   │   ├── recipient.js     # resolves a user id to a Keycloak account with an email
│   │   └── error.js         # UnprocessableEventError, never retried
│   ├── model/               # read-only Sequelize models: product, sales_order, sales_order_detail, payment, refund
│   ├── repository/
│   │   └── order.js         # read queries plus mapping, ids to numbers, money kept as strings
│   ├── service/
│   │   ├── account.js       # Keycloak client_credentials token cache and admin user lookup
│   │   └── mail.js          # sendEmail wrapper, From = MAIL_FROM or SMTP_USER
│   └── template/
│       ├── account.js       # account email texts
│       ├── checkout.js      # expired-order and payment email texts
│       ├── refund.js        # cancellation refund email text
│       ├── cart.js          # expired-cart email text, singular or plural
│       └── format.js        # IDR money, WIB dates, account masking, item lines, greeting
└── tests/                   # Jest suites mirroring src/, plus fixtures/
```

## Design Notes

- **Ids, not snapshots, for order events.** `order-service` sends only the id of the row, after the transaction commits. The email always reflects committed state and the message cannot leak or go stale. The cost is that this service must read the order-service tables directly, which couples it to that schema (`src/model/*` mirrors the JPA entities and the Liquibase migrations).
- **Read-only database access.** The Sequelize models use `timestamps: false` and a `defaultScope` of `is_deleted = false`, and `sync()` is never called. Products on an order are read `unscoped()`, so an order line keeps its product name after the product is soft-deleted.
- **Keycloak owns identity.** Customer email addresses and names are never copied into the ecommerce database. The service account has only `view-users`. Tokens are cached until 30 seconds before expiry and refreshed once on a `401`. A `404` means the user was deleted and is treated as unprocessable, not as a transient failure.
- **At-least-once delivery.** A message is acked only after SMTP accepts the email. A crash between `sendMail` and `ack` causes a redelivery, which can send a duplicate email. No deduplication by `eventId` is implemented.
- **One retry, then drop.** Requeueing happens only on the first delivery (`msg.fields.redelivered` is false). There is no dead-letter queue, so a message that fails twice is lost and is visible only in the logs.
- **Cart batching is in memory and per process.** Batches live in a `Map` keyed by `userId`. With several replicas, one customer's messages can be spread across instances and produce more than one email. Delivery is still safe, because unflushed messages are unacked.
- **Plain-text emails** keep templates simple and stop the generated password from being altered by HTML rendering.

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Process exits with `Failed to connect to database` | PostgreSQL not up, or wrong `DB_*` | Start the infra (`./build.sh postgres`) and check `DB_HOST`/`DB_PORT`. On the host, use `localhost`. |
| Process exits with `Failed to connect to broker` or `ACCESS_REFUSED` | RabbitMQ not up, or credentials not URL-encoded | Check `BROKER_URL`. `@` in the password must be `%40` (`p%40ssw0rd`). |
| Log shows `Broker connection closed, shutting down` after a while | The broker closed the channel, for example because the consumer ack timeout was exceeded by a long `CART_BATCH_WINDOW_MS` | Keep the window well below 30 minutes. Compose restarts the container. |
| `SMTP Connection Error` with a TLS or STARTTLS message | STARTTLS required against a server without TLS (Mailpit) | Set `SMTP_REQUIRE_TLS=false` for Mailpit. Use `SMTP_PORT=465` for implicit TLS. |
| `Keycloak token request failed with status 401` | Wrong `KEYCLOAK_CLIENT_ID`/`SECRET`, or realm not provisioned | Run `./app/init/keycloak-init.sh` (or `./build.sh`) and match `NOTIFICATION_SERVICE_CLIENT_SECRET`. |
| `Keycloak user lookup ... failed with status 403` | The service account lacks `view-users` | Re-run `keycloak-init.sh`, which grants it through `KEYCLOAK_USER_READER_CLIENTS`. |
| Order emails dropped with `Sales order ... not found` / `Payment ... not found` | Wrong database, or the row is soft-deleted | Point `DB_NAME` at the database `order-service` writes to. |
| `belongs to user ..., who no longer exists` | The customer was deleted in Keycloak | Expected. The message is dropped on purpose. |
| Expired-cart email arrives late | Working as designed: it waits `CART_BATCH_WINDOW_MS` after the first expiry | Lower `CART_BATCH_WINDOW_MS` for local testing, for example `10000`. |
| Cart messages pile up as *Unacked* in the management UI | Normal during a batch window. If they never drain, `CART_PREFETCH` is too low or the process is stuck | Raise `CART_PREFETCH`, check the logs, and restart the service. |
| Messages split between two consumers | A local `yarn start` and the compose container both running | Stop one of them. |
| No email in Mailpit and no error logged | Message routed nowhere: the queues did not exist yet when it was published | Start this service once so it declares and binds the queues. Check `routed` in the publish response. |
