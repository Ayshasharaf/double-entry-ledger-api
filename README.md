[![CI](https://github.com/Ayshasharaf/double-entry-ledger-api/actions/workflows/ci.yml/badge.svg)](https://github.com/Ayshasharaf/double-entry-ledger-api/actions/workflows/ci.yml)

# DoubleLedger — ledger-core

A **double-entry financial ledger** built with **Java 21**, **Spring Boot 4**, and **PostgreSQL**.

Money never appears or disappears — it only moves. Every transaction is recorded as balanced legs (debits = credits), stored atomically, and protected against duplicate requests and concurrent race conditions.

This repository is a learning and demo project with a double-entry ledger core and a wallet top-up feature. The ledger supports posting, reversals, idempotency, and concurrency controls. Wallet top-ups use a fake payment provider and post to the ledger. It is intentionally **not** a production payment platform (see [Limitations](#limitations)).

---

## What this project is

**DoubleLedger** is a **ledger API with a wallet product layer**: create accounts, post **balanced multi-leg journal entries**, read balances and transactions, reverse postings, and demonstrate user wallet top-ups through a fake provider. PostgreSQL is the source of truth for balances and transaction history.

It is **not** a payment processor (Stripe) or a full bank core. It sits in the same category as the **ledger layer** in products like **[Modern Treasury](https://www.moderntreasury.com/)** and **[Formance Ledger](https://docs.formance.com/modules/ledger)** — the service that owns **double-entry books** while payments, login, and cards live elsewhere.

```
Client
  │  Ledger API or wallet top-up API
        ▼
DoubleLedger (one Spring Boot app)   ← ledger core + wallet/top-up workflow
        ▼
PostgreSQL                          ← journals, entries, wallets, top-ups
```

**Who uses it:** backend developers exploring ledger and wallet design through a local REST API or Docker. The ledger API is unauthenticated. The wallet demo trusts `X-User-Id`; this is not production authentication. Do not expose the app publicly as-is.

**Related docs in this repo:** [UNDERSTANDING.md](./UNDERSTANDING.md) (accounting intuition), [CODEBASE_LEARNING.md](./CODEBASE_LEARNING.md) (guided study), [ARCHITECTURE_TEACHING_GUIDE.md](./ARCHITECTURE_TEACHING_GUIDE.md) (layer-by-layer tour).

---

## Table of contents

1. [What this project is](#what-this-project-is)
2. [Quick start](#quick-start)
3. [Walkthrough — first transfer end-to-end](#walkthrough--first-transfer-end-to-end)
4. [API reference](#api-reference)
5. [Is the app "complete"?](#is-the-app-complete)
6. [Implementation roadmap](#implementation-roadmap)
7. [Architecture overview](#architecture-overview)
8. [Code map](#code-map)
9. [Data flow — start to finish](#data-flow--start-to-finish)
10. [Database design](#database-design)
11. [Rules & principles](#rules--principles)
12. [Testing](#testing)
13. [Docker — what it does here](#docker--what-it-does-here)
14. [CI and CD](#ci-and-cd)
15. [API examples](#api-examples)
16. [What I learned](#what-i-learned)
17. [Limitations](#limitations)
18. [Tech stack](#tech-stack)
19. [Further reading](#further-reading)

---

## Quick start

### Prerequisites


| Requirement    | Why                                                                            |
| -------------- | ------------------------------------------------------------------------------ |
| **Java 21+**   | Project target (`pom.xml`). Java 26 works too.                                 |
| **Maven**      | Included via `./mvnw` wrapper — no separate install needed.                    |
| **PostgreSQL** | Required to run the app locally.                                               |
| **Docker**     | Required for integration tests (Testcontainers). Optional for running the app. |


**Java version:** the project targets **Java 21** (`pom.xml`). If `./mvnw` fails with a Java version error, point `JAVA_HOME` at JDK 21+ (e.g. `brew install openjdk@21` on macOS).

### Run the app

From the repository root, start the app and PostgreSQL together:

```bash
docker compose up --build
```

The API listens on port `8080`; PostgreSQL is exposed on `5433`. Flyway applies migrations at startup. To use local PostgreSQL instead, start a database matching `ledger/src/main/resources/application.properties`, then run `cd ledger && ./mvnw spring-boot:run`.

Try the API using [Swagger UI](http://localhost:8080/swagger-ui.html) or the [OpenAPI document](http://localhost:8080/api-docs). Ledger posting and reversal requests require an `Idempotency-Key` header. The wallet top-up demo additionally requires `X-User-Id`.

### Run tests

Run these Maven commands from the `ledger/` directory:

```bash
# All tests
./mvnw clean test

# Unit tests only (no Docker required)
./mvnw test -Dtest="AccountTest,RequestHasherTest,LedgerPostingServiceValidationTest,AccountBalanceServiceTest"

# Integration tests only (requires Docker)
./mvnw clean test -Dtest="*IntegrationTest*,LedgerApplicationTests"

# One test class
./mvnw test -Dtest=IdempotencyIntegrationTest

# One test method
./mvnw test -Dtest=IdempotencyIntegrationTest#sequentialReplay_returns200WithReplayHeader

# Top-up integration tests
./mvnw -Dtest=TopupIntegrationTest test
```

**Expected result when healthy:**

```
Tests run: 45, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- The current suite has **45 tests**; the verified run completed with 0 failures and 0 errors.
- Integration tests use Testcontainers when Docker is available. Alternatively, use local PostgreSQL on `localhost:5433` with `-Dtest.useLocalPostgres=true`.

---

## Walkthrough — first transfer end-to-end

With the app running (`docker compose up --build` or `cd ledger && ./mvnw spring-boot:run` + Postgres on port **5433**), run these steps. Amounts are **minor units** (500 = $5.00 USD). This ledger-only example creates an asset account; it is not the product `Wallet` row used by the M1 top-up endpoint. The seeded M1 wallet maps to a credit-normal liability account.

**1. Create a wallet (asset account)**

```bash
WALLET=$(curl -s -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{
    "name": "wallet",
    "accountType": "asset",
    "normalBalance": "D",
    "currency": "USD",
    "allowOverdraft": false,
    "overdraftLimitMinorUnits": 0
  }' | jq -r '.id')
echo "wallet=$WALLET"
```

**2. Create an equity account (for opening balance — same pattern tests use internally)**

```bash
EQUITY=$(curl -s -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{
    "name": "opening-equity",
    "accountType": "equity",
    "normalBalance": "C",
    "currency": "USD",
    "allowOverdraft": false,
    "overdraftLimitMinorUnits": 0
  }' | jq -r '.id')
echo "equity=$EQUITY"
```

**3. Fund the wallet ($100.00)** — balanced entry: debit wallet, credit equity

```bash
curl -s -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen | tr '[:upper:]' '[:lower:]')" \
  -d "{
    \"description\": \"opening balance\",
    \"legs\": [
      { \"accountId\": \"$WALLET\", \"amountMinorUnits\": 10000, \"direction\": \"DEBIT\" },
      { \"accountId\": \"$EQUITY\", \"amountMinorUnits\": 10000, \"direction\": \"CREDIT\" }
    ]
  }" | jq
```

**4. Create a pool account and transfer $5.00 wallet → pool**

```bash
POOL=$(curl -s -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{
    "name": "cash-pool",
    "accountType": "asset",
    "normalBalance": "D",
    "currency": "USD",
    "allowOverdraft": false,
    "overdraftLimitMinorUnits": 0
  }' | jq -r '.id')

IDEM=$(uuidgen | tr '[:upper:]' '[:lower:]')
curl -s -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEM" \
  -d "{
    \"description\": \"transfer to pool\",
    \"legs\": [
      { \"accountId\": \"$WALLET\", \"amountMinorUnits\": 500, \"direction\": \"CREDIT\" },
      { \"accountId\": \"$POOL\",   \"amountMinorUnits\": 500, \"direction\": \"DEBIT\"  }
    ]
  }" | jq
```

**5. Check balances**

```bash
curl -s "http://localhost:8080/api/v1/accounts/$WALLET" | jq '.balanceMinorUnits'
curl -s "http://localhost:8080/api/v1/accounts/$POOL" | jq '.balanceMinorUnits'
```

**6. Retry the same transfer** (same `Idempotency-Key`) — expect `200 OK` and header `Idempotent-Replayed: true`

```bash
curl -s -D - -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEM" \
  -d "{
    \"description\": \"transfer to pool\",
    \"legs\": [
      { \"accountId\": \"$WALLET\", \"amountMinorUnits\": 500, \"direction\": \"CREDIT\" },
      { \"accountId\": \"$POOL\",   \"amountMinorUnits\": 500, \"direction\": \"DEBIT\"  }
    ]
  }" -o /dev/null
```

On macOS, `uuidgen` produces UUIDs suitable for `Idempotency-Key`. On Linux, use `uuidgen` or any fresh UUID v4 string.

---

## API reference

Base URL: `http://localhost:8080/api/v1`

### Swagger / OpenAPI

| Resource | URL |
| -------- | --- |
| **Swagger UI** (try endpoints) | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) |
| **OpenAPI 3 JSON** | [http://localhost:8080/api-docs](http://localhost:8080/api-docs) |

Docs are generated at runtime with **[springdoc-openapi](https://springdoc.org/)** (`OpenApiConfig` sets title **DoubleLedger API** and a local server URL). DTOs use `@Schema` descriptions where helpful; `PostTransactionRequest` includes a sample two-leg transfer for **Try it out**.

**Headers in Swagger UI:** for `POST /transactions` and `POST /transactions/{journalEntryId}/reversals`, set **`Idempotency-Key`** to a UUID on each new attempt (reuse the same key only when you intend a replay).

| Method | Path                                       | Idempotency-Key     | Description                        |
| ------ | ------------------------------------------ | ------------------- | ---------------------------------- |
| `POST` | `/accounts`                                | No                  | Create account                     |
| `GET`  | `/accounts`                                | No                  | List all accounts                  |
| `GET`  | `/accounts/{id}`                           | No                  | Get one account (includes balance) |
| `POST` | `/transactions`                            | **Required** (UUID) | Post balanced journal entry        |
| `GET`  | `/transactions/{journalEntryId}`           | No                  | Read entry, legs, metadata         |
| `POST` | `/transactions/{journalEntryId}/reversals` | **Required** (UUID) | Reverse a posted entry             |
| `POST` | `/wallets/{walletId}/topups`                | **Required**        | Demo top-up using the fake provider |


**Account types:** `asset`, `liability`, `equity`, `revenue`, `expense`. **Normal balance:** `D` or `C`. **Leg direction:** `DEBIT` or `CREDIT`. All legs in one transaction must share one **currency** and sum to zero.

### Wallet top-up API

The current endpoint is `POST /api/v1/wallets/{walletId}/topups`. It expects `X-User-Id`, `Idempotency-Key`, and a positive `amountMinorUnits` JSON field. The seeded demo wallet belongs to `demo-user`. Amounts are USD minor units: `500` means `$5.00`.

```bash
curl -i -X POST http://localhost:8080/api/v1/wallets/bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1/topups \
  -H "Content-Type: application/json" \
  -H "X-User-Id: demo-user" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -d '{"amountMinorUnits":500}'
```

The fake provider declines amount `999999` to exercise the `FAILED` path. This is the current implementation contract; the project checklist still describes a different route/body shape, so reconcile those before treating the endpoint contract as final.

---

## Is the app "complete"?

The ledger core and wallet demo have separate completion scopes. The current full suite passes, but M1 still has work before the wallet milestone is complete.


| Check                    | How to verify                                                      |
| ------------------------ | ------------------------------------------------------------------ |
| Spring context starts    | `LedgerApplicationTests.contextLoads` passes                       |
| Database schema is valid | Flyway migrations `V1` through `V4` run without error              |
| Posting works            | Integration tests pass (balance updates, zero-sum enforced)        |
| Reversals work           | `ReversalIntegrationTest` passes                                   |
| Idempotency works        | 50 concurrent retries → 1 create + 49 replays                      |
| Concurrency is safe      | 10 parallel withdrawals → exactly 1 rejection, no deadlock         |
| Unit tests pass          | `AccountTest`, `RequestHasherTest`, validation tests (no Docker)   |
| API is reachable         | `POST /api/v1/accounts` and `POST /api/v1/transactions` return 201 |
| Top-up behavior          | `TopupIntegrationTest` passes (7 API/database integration tests)   |


Features **outside this project’s scope** (auth, outbox, idempotency TTL, etc.) are listed under [Limitations](#limitations) — not planned in this repo for now.

---

## Implementation roadmap

The work stays in one Spring Boot application. The ledger owns accounting records; the wallet package owns user-facing wallet workflows and calls the ledger service for postings.

| Phase | Scope | Status / exit criteria |
| --- | --- | --- |
| Ledger core | Accounts, balanced journals, derived balances, idempotency, concurrency, reversals | Implemented; current full suite passes 45 tests |
| M1: Wallet top-up | Seeded USD wallet, fake provider, owner check, top-up states, ledger posting | Implemented and covered by 7 integration tests; finalize the API contract and add in-flight/concurrent retry coverage before calling M1 done |
| M2: P2P transfer | Transfer between owned wallets; reject self-transfer and insufficient funds | Planned after M1 is closed |
| M3: Withdrawal | Fake payout flow and clearing-account ledger posting | Planned after M2 |
| M4: Demo polish | Read current user's wallet and derived balance; OpenAPI examples; run guide | Planned |
| Payment intent | Deterministic intent state machine with confirmation and step-up; chat adapter later | Future phase; an LLM must never post directly to the ledger |

### M1 completion checklist

- [ ] Choose one top-up route/body contract and align controller, checklist, and README.
- [ ] Prove a matching request during `PROCESSING` cannot cause a second effective provider charge or duplicate journal.
- [ ] Ensure a real provider adapter honors the same idempotency key across retries; the fake provider does not model persistent charge deduplication.
- [x] Verify success, replay, mismatch, decline, wrong owner, missing wallet, and invalid amount paths.
- [x] Run all 45 tests successfully.

See [`checklist.md`](checklist.md) for the detailed milestones and [`docs/phase-0-payment-intent.md`](docs/phase-0-payment-intent.md) for the later payment-intent product contract.

---

## Architecture overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         HTTP Client                              │
└────────────────────────────┬────────────────────────────────────┘
                             │  POST /api/v1/transactions
                             │  Header: Idempotency-Key
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  controller/                                                     │
│  LedgerController · TopupController · GlobalExceptionHandler     │
└────────────────────────────┬────────────────────────────────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
┌──────────────────┐ ┌─────────────┐ ┌──────────────────┐
│ IdempotencyService│ │RequestHasher│ │TopupService      │
│ (ledger API cache)│ │ (SHA-256)   │ │PaymentProvider   │
└────────┬─────────┘ └─────────────┘ └────────┬─────────┘
         │                                     │
         └──────────────┼──────────────────────┘
                        ▼
              ┌──────────────────┐
              │LedgerPostingService│
              │ (accounting core)│
              └────────┬─────────┘
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│  repository/          JPA + Hibernate                            │
│  AccountRepository · JournalEntryRepository · LedgerEntryRepository│
│  IdempotencyKeyRepository                                         │
└────────────────────────────┬────────────────────────────────────┘
                             │  SELECT ... FOR UPDATE (row locks)
                             │  pg_advisory_xact_lock (idempotency)
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  PostgreSQL                                                      │
│  Flyway migrations · constraint triggers · ACID transactions     │
└─────────────────────────────────────────────────────────────────┘
```

### Layer responsibilities


| Layer                    | Responsibility                                              |
| ------------------------ | ----------------------------------------------------------- |
| **Controller**           | HTTP in/out, request validation, header/path extraction     |
| **TopupService**         | Wallet ownership, idempotency, provider/ledger coordination  |
| **PaymentProvider**      | Charge abstraction; fake implementation for the demo        |
| **IdempotencyService**   | API-layer dedup — replay cached HTTP responses on retry     |
| **LedgerPostingService** | Domain logic — lock accounts, apply legs, update balances   |
| **Account (entity)**     | Debit/credit rules per account type, overdraft checks       |
| **Repository**           | Database access with pessimistic write locks                |
| **PostgreSQL**           | Final backstop — zero-sum trigger, unique constraints, ACID |


---

## Code map

```
ledger/
├── src/main/java/com/doubleledger/ledger/
│   ├── LedgerApplication.java          ← Spring Boot entry point
│   │
│   ├── controller/
│   │   ├── LedgerController.java       ← REST API (/accounts, /transactions)
│   │   └── GlobalExceptionHandler.java ← 400 / 409 / 500 error responses
│   │
│   ├── dto/                            ← Request/response shapes (no business logic)
│   │   ├── CreateAccountRequest.java
│   │   ├── PostTransactionRequest.java
│   │   ├── TransactionLegDto.java
│   │   └── *Response.java
│   │
│   ├── service/
│   │   ├── LedgerPostingService.java   ← Core posting engine + reversals
│   │   ├── IdempotencyService.java     ← API idempotency cache
│   │   ├── AccountService.java         ← Account CRUD
│   │   ├── AccountBalanceService.java  ← Derived balance math
│   │   └── JournalEntryResponseService.java
│   │
│   ├── util/
│   │   └── RequestHasher.java          ← SHA-256 fingerprint of request body
│   │
│   ├── audit/
│   │   └── ForensicAuditService.java   ← Structured error logging
│   │
│   ├── persistence/
│   │   └── PostgresAdvisoryLockService.java
│   │
│   ├── repository/                     ← Spring Data JPA interfaces
│   │   ├── AccountRepository.java      ← includes findAllByIdsForUpdate()
│   │   ├── JournalEntryRepository.java
│   │   ├── LedgerEntryRepository.java
│   │   └── IdempotencyKeyRepository.java
│   │
│   ├── model/                          ← JPA entities ↔ database tables
│   │   ├── Account.java                ← validateBalance(), overdraft rules
│   │   ├── JournalEntry.java           ← transaction header
│   │   ├── LedgerEntry.java            ← one leg (signed amount)
│   │   └── IdempotencyKey.java         ← cached API response
│   │
│   └── config/
│       └── AsyncConfig.java
│
├── src/main/java/com/doubleledger/wallet/
│   ├── controller/TopupController.java       ← Top-up HTTP endpoint
│   ├── dto/                                   ← Top-up request/response
│   ├── model/                                 ← Wallet, Topup, TopupStatus
│   ├── provider/                              ← PaymentProvider + fake provider
│   ├── repository/                            ← Wallet and top-up persistence
│   └── service/TopupService.java              ← Top-up orchestration
│
├── src/main/resources/
│   ├── application.properties          ← DB connection, Flyway, logging
│   └── db/migration/
│       ├── V1__initial_ledger_schema.sql
│       ├── V2__derived_balances_and_immutable_postings.sql
│       ├── V3__pending_and_voided.sql
│       └── V4__wallets_and_topups.sql
│
├── docker-compose.yml                  ← app + Postgres, one command
├── Dockerfile
│
└── src/test/java/com/doubleledger/ledger/
    ├── support/
    │   └── PostgresIntegrationTestSupport.java  ← Testcontainers + helpers
    ├── AccountTest.java                         ← unit: overdraft / frozen
    ├── util/RequestHasherTest.java              ← unit: hash stability
    ├── service/
    │   ├── LedgerPostingServiceValidationTest.java ← unit: zero-sum
    │   └── AccountBalanceServiceTest.java       ← unit: balance math
    ├── LedgerApplicationTests.java              ← smoke test
    ├── IdempotencyIntegrationTest.java          ← HTTP idempotency (3 tests)
    ├── LedgerPostingConcurrencyIntegrationTest.java ← locking (2 tests)
    ├── PendingJournalIntegrationTest.java       ← pending journals (1 test)
    ├── ReversalIntegrationTest.java             ← reversals (9 tests)
    ├── TransactionReadIntegrationTest.java      ← GET + metadata (3 tests)
    └── TopupIntegrationTest.java                ← wallet top-ups (7 tests)
```

---

## Data flow — start to finish

### Example: transfer $5.00 from Wallet → Pool

**Request:**

```http
POST /api/v1/transactions
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000

{
  "description": "transfer to pool",
  "legs": [
    { "accountId": "<wallet-uuid>", "amountMinorUnits": 500, "direction": "CREDIT" },
    { "accountId": "<pool-uuid>",   "amountMinorUnits": 500, "direction": "DEBIT"  }
  ]
}
```

> Amounts are in **minor units** (cents). 500 = $5.00. No floats anywhere.

### Step-by-step flow

```mermaid
sequenceDiagram
    participant Client
    participant Controller as LedgerController
    participant Hasher as RequestHasher
    participant Idem as IdempotencyService
    participant Post as LedgerPostingService
    participant DB as PostgreSQL

    Client->>Controller: POST /transactions + Idempotency-Key
    Controller->>Hasher: hash request body (SHA-256)
    Controller->>Idem: executePostTransaction(key, hash, postingAction)

    Idem->>DB: pg_advisory_xact_lock (API key)
    Idem->>DB: lookup idempotency_keys

    alt Key exists + same hash
        Idem-->>Controller: cached response (200 OK, Idempotent-Replayed: true)
    else Key exists + different hash
        Idem-->>Controller: 409 Conflict
    else New request
        Idem->>Post: postTransaction(request)
        Post->>DB: pg_advisory_xact_lock (domain key)
        Post->>Post: validate legs sum to zero
        Post->>DB: SELECT accounts FOR UPDATE (sorted by id)
        Post->>Post: debit/credit each account
        Post->>DB: INSERT journal_entry + ledger_entries
        Post->>DB: UPDATE account balances
        DB->>DB: deferred trigger checks SUM(amount) = 0
        Post-->>Idem: JournalEntry
        Idem->>DB: INSERT idempotency_keys (cache response)
        Idem-->>Controller: new response (201 Created)
    end

    Controller-->>Client: JSON JournalEntryResponse
```



### What gets written to the database

For one $5.00 transfer, three rows change:

```
journal_entries          ledger_entries                    accounts
─────────────────        ─────────────────────────────     ────────────────
id: je-001               leg 1: wallet  amount = +500      wallet: 9500 → 9000
idempotency_key: ...     leg 2: pool    amount = -500      pool:   0    → 500
description: "transfer"  balance_after on each leg
status: posted
```

Signed amounts: **negative = debit**, **positive = credit**. They must sum to **zero** per journal entry (enforced by a deferred DB trigger at commit time).

---

## Database design

```mermaid
erDiagram
    accounts ||--o{ ledger_entries : "has legs"
    journal_entries ||--|{ ledger_entries : "contains"
    journal_entries {
        uuid id PK
        uuid idempotency_key UK
        string status
        uuid reverses_journal_entry_id FK
    }
    ledger_entries {
        uuid id PK
        uuid journal_entry_id FK
        uuid account_id FK
        bigint amount_minor_units
        bigint balance_after
    }
    accounts {
      uuid id PK
      string account_type
      string currency
      boolean allow_overdraft
    }
    wallets {
      uuid id PK
      string user_id
      uuid ledger_account_id FK
      string currency
    }
    topups {
      uuid id PK
      uuid wallet_id FK
      bigint amount_minor_units
      string status
      string idempotency_key UK
      uuid journal_entry_id FK
    }
    idempotency_keys {
        text idempotency_key PK
        text request_hash
        int response_status
        text response_body
    }
```



### Two idempotency layers (not redundant)


| Layer      | Table                             | Purpose                                                                  |
| ---------- | --------------------------------- | ------------------------------------------------------------------------ |
| **API**    | `idempotency_keys`                | Client retried HTTP call → replay exact same JSON response               |
| **Domain** | `journal_entries.idempotency_key` | Same business event can't be posted twice, even via different code paths |


Both use **Postgres advisory locks** (`pg_advisory_xact_lock`) to serialize concurrent requests with the same key.

---

## Rules & principles

### Accounting rules

1. **Double-entry invariant** — every journal entry's legs sum to zero (debits = credits).
2. **No floats** — all money stored as `BIGINT` minor units (cents).
3. **Single currency per transaction** — multi-currency requires explicit FX/clearing accounts (v2).
4. **Immutability** — entries are never edited or deleted; corrections use reversals (`POST /transactions/{id}/reversals`).

### Concurrency rules

1. **Pessimistic locking** — accounts are locked with `SELECT ... FOR UPDATE` before balance changes.
2. **Sorted lock order** — account IDs are locked in sorted order to prevent deadlocks.
3. **Advisory locks** — idempotency keys are serialized at both API and domain layers.

### Defense in depth


| Check                     | Where                                | What happens on failure                |
| ------------------------- | ------------------------------------ | -------------------------------------- |
| Legs balance to zero      | Java (`LedgerPostingService`)        | `400 Bad Request` before DB write      |
| Legs balance to zero      | PostgreSQL trigger                   | Transaction rolled back — can't commit |
| Overdraft                 | Java (`Account.validateOverdraft()`) | `400 Bad Request`                      |
| Duplicate idempotency key | Java + DB unique constraint          | Replay or `409 Conflict`               |
| Same key, different body  | Java (`IdempotencyService`)          | `409 Conflict`                         |


### API rules

- Every `POST /transactions` requires an `Idempotency-Key` header (UUID).
- Request body `idempotencyKey` must match the header if present.
- First successful post → `201 Created`. Exact retry → `200 OK` + `Idempotent-Replayed: true`.

---

## Testing

### Philosophy

Integration tests boot the **real Spring application** against a **real PostgreSQL** database. Unit tests cover pure validation logic without Spring or Docker.

### Unit tests (no Docker)


| Test class                           | What it proves                                  |
| ------------------------------------ | ----------------------------------------------- |
| `AccountTest`                        | Overdraft limits, frozen account rejection      |
| `RequestHasherTest`                  | Same body → same hash; key order independence   |
| `LedgerPostingServiceValidationTest` | Unbalanced legs, invalid direction, missing key |
| `AccountBalanceServiceTest`          | Signed-leg balance math per account type        |


```bash
./mvnw test -Dtest="AccountTest,RequestHasherTest,LedgerPostingServiceValidationTest,AccountBalanceServiceTest"
```

### Integration tests (Docker required)

### Tools


| Tool                 | Role                                                 |
| -------------------- | ---------------------------------------------------- |
| **JUnit 5**          | Test framework (`@Test`, `@BeforeEach`)              |
| **Spring Boot Test** | `@SpringBootTest` — loads full application context   |
| **Testcontainers**   | Spins up `postgres:16-alpine` in Docker during tests |
| **AssertJ**          | Readable assertions (`assertThat(x).isEqualTo(y)`)   |
| **RestTestClient**   | HTTP tests without manually starting a server        |
| **Flyway**           | Same migrations run in tests as in production        |
| **Maven Surefire**   | Runs tests via `./mvnw test`                         |


### Test suite (45 tests)


| Test                                                          | Layer   | What it proves                                       |
| ------------------------------------------------------------- | ------- | ---------------------------------------------------- |
| `AccountTest` (6)                                             | Unit    | Overdraft / frozen / closed validation               |
| `RequestHasherTest` (5)                                       | Unit    | Deterministic request fingerprints                   |
| `LedgerPostingServiceValidationTest` (4)                      | Unit    | Zero-sum and leg validation                          |
| `AccountBalanceServiceTest` (4)                               | Unit    | Balance math per account type                        |
| `contextLoads`                                                | Smoke   | Spring Boot + Postgres + Flyway all wire up          |
| `sameIdempotencyKey_concurrentPosts_createSingleJournalEntry` | HTTP    | 50 threads, 1 create + 49 replays                    |
| `sameIdempotencyKey_differentPayload_returns409`              | HTTP    | Same key + different amount → conflict               |
| `sequentialReplay_returns200WithReplayHeader`                 | HTTP    | Retry returns cached response                        |
| `concurrentWithdrawals_exhaustBalanceExactlyOneFails`         | Service | 10 threads, balance for 9 → 1 rejection, balance = 0 |
| `crossAccountTransfers_doNotDeadlock`                         | Service | 40 bidirectional A↔B transfers, no deadlock          |
| `PendingJournalIntegrationTest` (1)                            | HTTP    | Pending journal lifecycle                            |
| `ReversalIntegrationTest` (9)                                  | HTTP    | Reversal restores balances, idempotency, 409/404     |
| `TransactionReadIntegrationTest` (3)                          | HTTP    | GET transaction returns legs + metadata              |
| `TopupIntegrationTest` (7)                                    | HTTP    | Top-up success/replay/failure/ownership/validation    |


### How tests get a database

```
PostgresIntegrationTestSupport (base class for all integration tests)
        │
        ├── Docker available?
        │     YES → Testcontainers starts postgres:16-alpine
        │           @DynamicPropertySource wires JDBC URL/username/password
        │
        └── NO  → Falls back to localhost:5433
                  (or force with -Dtest.useLocalPostgres=true)
```

Every test class extends `PostgresIntegrationTestSupport`, which provides:

- Automatic Postgres setup
- `createAssetAccount(name)` helper
- `buildTransfer(from, to, amount, idempotencyKey)` helper

---

## Docker

### Docker Compose — run app + Postgres

```bash
cd ledger
docker compose up --build
```

App: `http://localhost:8080` · Swagger: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) · Postgres: `localhost:5433`

### Docker for integration tests

```
┌──────────────────────────────────────────────────────────┐
│  ./mvnw test                                              │
│       │                                                   │
│       ▼                                                   │
│  Testcontainers ──► Docker ──► postgres:16-alpine         │
│       │                      (ephemeral, destroyed after) │
│       ▼                                                   │
│  Spring Boot test context connects to that Postgres       │
│  Flyway runs V1 through V4 migrations                     │
│  Tests run against real schema + real locks + real triggers│
└──────────────────────────────────────────────────────────┘
```


| Scenario                                | Docker needed?                                                   |
| --------------------------------------- | ---------------------------------------------------------------- |
| Run unit tests                          | **No**                                                           |
| Run integration tests                   | **Yes** — Testcontainers requires Docker running                 |
| Run the app locally (`spring-boot:run`) | **No** — uses Postgres at `localhost:5433`                       |
| Run app via Docker Compose              | **Yes** — `docker compose up --build`                            |
| Run tests without Docker                | Use `-Dtest.useLocalPostgres=true` + local Postgres on port 5433 |


**Make sure Docker Desktop is running before integration tests or `./mvnw test`.**

---

## CI and CD

GitHub Actions runs `./mvnw clean test` on pushes to `main` or `feature/**` branches and on pull requests targeting `main`:

```
.github/workflows/ci.yml
```

The workflow uses Java 21 and `ubuntu-latest`; Docker is available there for the Testcontainers PostgreSQL integration tests. Feature branch pushes are checked before a pull request is opened.

**There is no CD/deployment workflow yet.** The current pipeline verifies the code; it does not publish an image or deploy the service. A sensible next step is to add deployment only after choosing a target (for example, a container registry plus a demo host), storing credentials as GitHub Actions secrets, and defining a staging/production approval policy. Do not describe the current Actions setup as continuous deployment.

---

## API examples

### Create an account

```bash
curl -s -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{
    "name": "wallet",
    "accountType": "asset",
    "normalBalance": "D",
    "currency": "USD",
    "allowOverdraft": false,
    "overdraftLimitMinorUnits": 0
  }' | jq
```

### Post a transaction

```bash
curl -s -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -d '{
    "description": "transfer to pool",
    "legs": [
      { "accountId": "<wallet-uuid>", "amountMinorUnits": 500, "direction": "CREDIT" },
      { "accountId": "<pool-uuid>",   "amountMinorUnits": 500, "direction": "DEBIT"  }
    ]
  }' | jq
```

### Get account balance

```bash
curl -s http://localhost:8080/api/v1/accounts/<account-uuid> | jq
```

### Reverse a transaction

```bash
curl -s -X POST "http://localhost:8080/api/v1/transactions/<journal-entry-uuid>/reversals" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 660e8400-e29b-41d4-a716-446655440001" \
  -d '{ "description": "undo transfer" }' | jq
```

### Get a transaction

```bash
curl -s http://localhost:8080/api/v1/transactions/<journal-entry-uuid> | jq
```

### Retry (idempotent replay)

Send the exact same request again with the same `Idempotency-Key`:

```
HTTP/1.1 200 OK
Idempotent-Replayed: true
```

---

## What I learned

Building this project was practice in **how fintech platforms keep books in software**, not just CRUD on a `balance` column.


| Topic                          | What the codebase teaches                                                                                                |
| ------------------------------ | ------------------------------------------------------------------------------------------------------------------------ |
| **Double-entry**               | Money moves via **balanced legs**; the DB trigger rejects commits where legs do not sum to zero.                         |
| **Minor units**                | Store money as `BIGINT` cents — never floating point.                                                                    |
| **Idempotency (Stripe-style)** | `Idempotency-Key` on POST plus a cached response table — safe retries without double-posting.                            |
| **Two idempotency layers**     | **API** cache vs **domain** `journal_entries.idempotency_key` — HTTP replay vs business-event dedup.                     |
| **Concurrency**                | `SELECT … FOR UPDATE` on accounts (sorted order), plus Postgres **advisory locks** on idempotency keys.                  |
| **Immutability**               | No editing postings; **reversals** create offsetting entries and link to the original.                                   |
| **Defense in depth**           | Validate in Java *and* enforce in PostgreSQL (triggers, uniqueness).                                                     |
| **Testing**                    | Unit tests for rules; **Testcontainers** integration tests for real locks, triggers, and HTTP.                           |
| **System design**              | Ledger is the accounting core; wallet/payment orchestration is a separate module in this app, not a separate deployable service. |


**Outside the current demo scope:** production authentication, multi-tenant isolation, idempotency TTL, and event outbox/webhooks. The wallet and transfer implementation plan is listed above.

**Contrast with channel auth (e.g. push approve on a phone):** that pattern proves a **human** on a **device**; this project focuses on **server integrators**, **posting invariants**, and **idempotent money APIs** — the layer behind an app after business approval.

---

## Limitations

This project is a **ledger core plus a wallet top-up demo** for learning and local use. Most items below are **intentional boundaries**. A few are **known integrity / API gaps** called out so they are not mistaken for production guarantees.

### Security and operations

| Limitation | Detail |
| ---------- | ------ |
| **No authentication or authorization** | Every endpoint is public. Anyone who can reach the server can read all accounts and post transactions. |
| **No multi-tenancy** | There is no `org_id` or tenant isolation. All accounts live in one shared namespace. |
| **No rate limiting or abuse controls** | No gateway, throttling, or IP allowlists. |
| **Secrets in config** | Database credentials in `application.properties` / Compose are for local dev only — not a production secret-management story. |
| **No durable actor audit trail** | Successful posts/reversals are not written to a separate who/when/why audit table. Forensic logging covers unexpected failures only (async logs), not compliance-grade audit. |

### API surface

| Limitation | Detail |
| ---------- | ------ |
| **No account lifecycle API** | Accounts can be `active`, `frozen`, or `closed` in the schema, but there is no HTTP API to freeze, close, or update an account after creation. |
| **No transaction listing** | You can `GET /transactions/{id}` but not list entries by account, date range, or cursor — no statements or reconciliation export API. |
| **`GET /accounts` is unpaginated** | Returns every account in the database; does not scale for large catalogs. |
| **Strict idempotency header** | `Idempotency-Key` must be a **UUID** string (see controller validation). Arbitrary opaque keys like some APIs allow are not supported. |
| **Weak Bean Validation on post DTOs** | `@Valid` is present, but posting legs/description have little declarative validation. Bad payloads often fail later in service/DB rather than as clean 400 field errors. |
| **Frozen/closed accounts return 500** | `Account.validateBalance` throws `IllegalStateException`, which maps to a generic internal error — clients cannot tell “not transactable” from infrastructure failure. |
| **Error bodies lack stable codes** | Insufficient funds, missing account, and unbalanced legs are mostly free-text `400` messages. Clients must parse strings; there is no machine-readable `errorCode` for retry vs fix-input. |

### Money and accounting

| Limitation | Detail |
| ---------- | ------ |
| **Single currency per journal entry** | All legs must share one currency. Cross-currency FX requires explicit clearing accounts — not modeled here (see Flyway `V1` comments). |
| **Currency is length-checked, not ISO-validated** | Account create accepts any 3-letter string (e.g. `ZZZ`); it is not checked against ISO 4217. |
| **Not a payment system** | No cards, ACH, wallets on rails, settlements, or fees as first-class products — only manual double-entry postings. |
| **Reversals only** | Posted entries are immutable. Corrections are **full reversals**, not partial edits or arbitrary adjustments via API. |
| **One-reversal invariant is app-enforced** | Concurrent double-reversal is blocked by advisory locks + service checks, but there is **no** partial `UNIQUE` index on `reverses_journal_entry_id`. A bypass (bug / raw SQL) can post two reversals. |
| **Leg amounts not enforced positive at the API** | Docs say amounts are unsigned minor units, but negative values are not rejected. A negative amount can invert debit/credit while still zero-summing. |
| **Empty journal bypasses zero-sum trigger** | The deferred balance trigger runs on `ledger_entries` inserts. A journal header with **zero legs** never fires it (app requires ≥2 legs; raw SQL could still create an empty header). |
| **Journal status transitions unrestricted** | Immutability triggers allow updating `status` to any value, including un-reversing (`reversed → posted`), without changing legs. |
| **`normal_balance` not tied to `account_type`** | Contradictory pairs can be stored; balance math uses account type only and ignores `normal_balance`. |

### Data, performance, and reliability

| Limitation | Detail |
| ---------- | ------ |
| **Derived balances** | Balances are computed from `ledger_entries` (stored balance column was removed in `V2`). Hot accounts pay read cost on every balance lookup. |
| **SUM under row locks on write** | Posting locks accounts with `SELECT … FOR UPDATE`, then recomputes `SUM(ledger_entries)` for each. Correct under concurrency, but hot accounts serialize and slow as history grows — no materialized balance tip. |
| **Idempotency keys never expire** | Rows in `idempotency_keys` are kept forever — no TTL or cleanup job; the table grows with every distinct POST key. |
| **Postgres unique-violation “replay” catch is fragile** | Catching `DataIntegrityViolationException` and re-querying in the **same** transaction is unreliable after Postgres aborts the TX. Advisory locks usually avoid the race; the catch path is not a true safety net. |
| **No event outbox or webhooks** | Downstream systems cannot subscribe to posting events from this service. |
| **No `failed_entries` persistence** | Failed posting attempts are logged (forensic audit) but not stored in a dedicated quarantine table for replay. |
| **Single Postgres instance** | No replication, sharding, or multi-region story in this repository — one database is the entire backstop. |
| **Isolation level not explicit** | The service relies on Postgres/Spring defaults (`READ COMMITTED`) plus pessimistic/advisory locks; isolation is not stated in config. |

### Product and scope

| Limitation | Detail |
| ---------- | ------ |
| **Learning / demo scale** | Tested with integration scenarios (concurrency, idempotency, reversals), not load-tested for high QPS or huge history. |
| **Not a bank or ERP** | No regulatory reporting, AML, end-of-day batch, chart-of-accounts workflows, or general ledger reporting packs. |

For accounting intuition and design tradeoffs, see [UNDERSTANDING.md](./UNDERSTANDING.md). For schema-level rules (triggers, immutability), see `src/main/resources/db/migration/V1__initial_ledger_schema.sql` and `V2__derived_balances_and_immutable_postings.sql`.

---

## Tech stack


| Component        | Version / choice                            |
| ---------------- | ------------------------------------------- |
| Java             | 21                                          |
| Spring Boot      | 4.1.0                                       |
| PostgreSQL       | 16 (Testcontainers)                         |
| Flyway           | Schema migrations                           |
| JPA / Hibernate  | ORM                                         |
| Testcontainers   | Integration test database                   |
| Jackson 2        | JSON serialization (`spring-boot-jackson2`) |
| springdoc-openapi | Swagger UI + OpenAPI 3 (`/swagger-ui.html`, `/api-docs`) |
| Logstash encoder | Structured ECS logging                      |


---

## Further reading

The original deep-dive design notes (TigerBeetle vs Formance comparisons, schema rationale, terminology) lived in earlier versions of this file. The schema migration at `src/main/resources/db/migration/V1__initial_ledger_schema.sql` is the authoritative source for database constraints and triggers.
