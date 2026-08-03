# DoubleLedger — terms, tests, and how to explain demos

Use this when you run the test suite, try Swagger, or walk someone through the project.

---

## 1. Words you should know

### Buckets (accounts)

Think of each **account** as a **bucket** that holds money in one currency. Money never appears from nowhere: every movement is a **transaction** with at least two **legs** (debit/credit) that **balance to zero**.

In demos and tests we often use three buckets:

| Name (example) | Account type | Role in the demo |
|----------------|--------------|------------------|
| **Wallet** | `asset` | “Customer cash” — where spendable balance lives. Normal balance **debit (D)**. |
| **Pool** | `asset` | “Treasury / settlement pot” — where transferred money goes. Also asset, normal **D**. |
| **Equity** | `equity` | “Opening balance source” — used only to **fund** the wallet in tests (like capital injected into the business). Normal balance **credit (C)**. |

These names are **labels**, not special API types. You could call them `alice`, `bob`, and `opening-equity`; wallet/pool/equity are just easy stories.

**Other account types** (supported by the system, less used in our tests):

- **Liability** — money you owe (credit-normal).
- **Revenue / expense** — P&amp;L style accounts.

### Transaction building blocks

| Term | Meaning |
|------|--------|
| **Leg** | One line: account + amount + direction (`DEBIT` or `CREDIT`). |
| **Journal entry** | One posted transaction (header + legs). API response **`id`** = **journal entry id**. |
| **Minor units** | Integer money (e.g. `500` = $5.00 if USD cents). |
| **Idempotency key** | Client UUID so retries do not double-post. Same key + same body → replay (`200`, header `Idempotent-Replayed: true`). Same key + different body → **409**. |
| **Reversal** | New journal entry that undoes a prior one; original marked `reversed`. |

### Funding the wallet (why equity exists in tests)

A new wallet starts at **0**. You cannot transfer out unless something **credits** the wallet first.

Tests use an **opening balance** entry:

- **DEBIT** wallet (increase asset)
- **CREDIT** equity (offset — “capital added”)

Then a **transfer** wallet → pool:

- **CREDIT** wallet (decrease asset)
- **DEBIT** pool (increase asset)

---

## 2. Test types in this repo

```
        ┌─────────────────────────────┐
        │  Integration (HTTP + Postgres) │
        └─────────────────────────────┘
        ┌─────────────────────────────┐
        │  Service unit (Mockito)      │
        └─────────────────────────────┘
        ┌─────────────────────────────┐
        │  Pure unit (no Spring/DB)    │
        └─────────────────────────────┘
```

### Pure unit tests (fast, no database)

| Class | What it proves |
|-------|----------------|
| `AccountTest` | Business rules on one account: overdraft allowed/limit, frozen/closed cannot post. |
| `RequestHasherTest` | Idempotency fingerprint: same payload → same hash; amount change → different hash; JSON map key order ignored. |
| `AccountBalanceServiceTest` | Debit/credit math for asset vs liability; “economic balance” sign conventions. |

**One-liner for others:** “These check rules and math in memory, in milliseconds.”

### Service unit tests (Mockito — no real DB)

| Class | What it proves |
|-------|----------------|
| `LedgerPostingServiceValidationTest` | Bad requests never hit the database: unbalanced legs, single leg, invalid direction, missing idempotency key. |

**One-liner:** “We verify validation fails **before** any save — using fake repositories.”

### Integration tests (Spring Boot + real Postgres)

Uses `PostgresIntegrationTestSupport`: Testcontainers Postgres if Docker is available, else local `localhost:5433`.

| Class | What it proves |
|-------|----------------|
| `LedgerApplicationTests` | Application context loads with real DB wiring. |
| `TransactionReadIntegrationTest` | POST persists metadata and legs; GET returns them; unknown id → 404. |
| `IdempotencyIntegrationTest` | 50 concurrent posts, same key → **one** journal entry; replay → 200 + header; conflicting payload → 409. |
| `LedgerPostingConcurrencyIntegrationTest` | Race on last dollar: N−1 withdrawals succeed, 1 fails; cross-account transfers do not deadlock. |
| `ReversalIntegrationTest` | Reverse restores balances, marks original reversed, idempotent reversal behavior. |

**One-liner for others:** “These hit the real API and database — concurrency, idempotency, and reversals behave like production.”

---

## 3. Run automated tests

From the `ledger` directory:

```bash
./mvnw test
```

Only unit tests (no Docker / no local Postgres on 5433):

```bash
./mvnw test -Dtest='AccountTest,RequestHasherTest,AccountBalanceServiceTest,LedgerPostingServiceValidationTest'
```

Force local Postgres instead of Testcontainers:

```bash
./mvnw test -Dtest.useLocalPostgres=true
```

---

## 4. Manual test (Swagger or curl)

**Prerequisites:** Postgres up (`docker compose up postgres` or full stack).

1. **Create wallet** — `POST /api/v1/accounts` (`accountType`: `asset`, `normalBalance`: `D`).
2. **Create pool** — same.
3. **Create equity** — `accountType`: `equity`, `normalBalance`: `C`.
4. **Fund wallet** — `POST /api/v1/transactions` with header `Idempotency-Key` (UUID) matching body `idempotencyKey`:
   - **Two legs** (Swagger should pre-fill both): DEBIT wallet, CREDIT equity, same `amountMinorUnits`.
5. **Transfer** — new idempotency UUID; **two legs**: CREDIT wallet, DEBIT pool (replace placeholder `accountId`s with ids from step 1–3).
6. **Read** — `GET /api/v1/transactions/{id}` where `{id}` is the **`id`** field from step 5 response.
7. **Optional reverse** — `POST /api/v1/transactions/{id}/reversals` with a **new** `Idempotency-Key`.

Swagger UI: `http://localhost:8080/swagger-ui.html`

---

## 5. Script: explaining while you demo (2 minutes)

> “This is a **double-entry ledger**. Every account is a **bucket**. We use a **wallet** and **pool** as two asset buckets, and **equity** only to inject starting money — like seed funding.
>
> A **transaction** always has balanced **legs**: money leaving one bucket equals money entering another. Amounts are in **minor units** (cents).
>
> **Idempotency** means if the client retries with the same key, we don’t charge twice — we return the original **journal entry** `id`.
>
> **Automated tests**: fast **unit** tests for rules and hashing; **Mockito** tests that bad payloads never save; **integration** tests with Postgres for concurrency, idempotency under 50 threads, and reversals.
>
> I can run `./mvnw test` to show everything green, or walk through Swagger: create buckets → fund → transfer → GET by journal id.”

---

## 6. Map: demo step → automated test coverage

| Demo step | Covered by |
|-----------|------------|
| Create accounts | Integration tests (`createAssetAccount`, REST flows) |
| Fund (opening balance) | `PostgresIntegrationTestSupport.fundAccount` / posting service |
| Transfer | `TransactionReadIntegrationTest`, idempotency tests |
| Same key retry | `IdempotencyIntegrationTest.sequentialReplay_*` |
| Conflict on same key, different amount | `IdempotencyIntegrationTest.sameIdempotencyKey_differentPayload_returns409` |
| Not enough balance | `LedgerPostingConcurrencyIntegrationTest.concurrentWithdrawals_*` |
| Reverse | `ReversalIntegrationTest` |
| Invalid JSON rules | `LedgerPostingServiceValidationTest`, `AccountTest` |

---

## 7. IDs cheat sheet (avoid confusion when testing)

| Field | Where you get it | Used for |
|-------|------------------|----------|
| Account `id` | Response of `POST /accounts` | `accountId` in transaction legs |
| Journal entry `id` | Response of `POST /transactions` | GET `/transactions/{id}`, reversals |
| `idempotencyKey` | You generate a new UUID per logical operation | Header `Idempotency-Key` + request body (must match) |

Do **not** use account id as journal entry id, or reuse idempotency keys across different operations.
