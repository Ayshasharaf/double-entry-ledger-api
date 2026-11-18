# E-wallet checklist

Use this in order. Do not skip Milestone 1 items.

Stay in **one Spring Boot app** (two packages). Split into two services later.

Conversational payments (after M2): product contract is [docs/phase-0-payment-intent.md](./docs/phase-0-payment-intent.md). Same GitHub repo, new packages — not a second repository.

**Demo defaults:** `X-User-Id` auth, USD only, fake provider that can succeed or fail.

**Branch:** `git checkout -b feature/wallet-topup`

---

## Before you code

- [ ] Stay in one Spring Boot app (`com.doubleledger.ledger` + `com.doubleledger.wallet`)
- [ ] Auth: `X-User-Id` header (JWT later)
- [ ] Currency: USD only
- [ ] Fake provider: succeed **and** fail (fail = no journal)
- [ ] Create branch `feature/wallet-topup`

---

## Milestone 1 — top-up only

### 1. Schema

- [x] Flyway `V4`: `wallets` (`id`, `user_id`, `ledger_account_id`, `currency`)
- [x] Flyway `V4`: `topups` (`id`, `wallet_id`, `amount_minor_units`, `currency`, `status`, `idempotency_key` UNIQUE, `request_hash`, `provider_ref`, `journal_entry_id`)
- [x] Seed one **Provider Clearing** ledger account
- [x] Seed one demo wallet + user ledger account (or create on first use — pick one and stick to it)

### 2. Packages (do not put this in `ledger`)

- [ ] `com.doubleledger.wallet.controller`
- [ ] `com.doubleledger.wallet.service`
- [ ] `com.doubleledger.wallet.provider`
- [ ] `com.doubleledger.wallet.model` + `repository`

### 3. Provider

- [ ] `PaymentProvider` interface (`charge(amount, currency, idempotencyKey)`)
- [ ] `FakePaymentProvider` (no real Stripe)
- [ ] Never call the provider inside an open `@Transactional`

### 4. API

- [ ] `POST /api/v1/topups` + header `Idempotency-Key`
- [ ] Body: `walletId`, `amountMinorUnits`
- [ ] Require `X-User-Id`; wallet must belong to that user (403 if not)

### 5. Flow

- [ ] Same key + same body → return existing top-up, **do not charge again**
- [ ] Same key + different body → 409
- [ ] Insert `PROCESSING` and **commit**
- [ ] Call fake provider **with no DB tx**
- [ ] Provider fail → `FAILED`, **no journal**
- [ ] Provider ok → one tx: `SUCCEEDED` + ledger post  
      **Dr Provider Clearing / Cr User Wallet**
- [ ] Ledger journal uses the same business key so a retry cannot post twice

### 6. Tests (Testcontainers)

- [ ] Retry same top-up → **one** `topups` row, **one** journal, balance once
- [ ] In-flight `PROCESSING` replay does not double-charge
- [ ] Provider fail → `FAILED`, wallet balance unchanged
- [ ] Wrong user → 403
- [ ] Mismatched idempotency body → 409

### 7. Git

- [ ] Commits: schema → provider → endpoint → tests
- [ ] One PR: `Add wallet top-up via fake provider`
- [ ] Do not mix ledger refactors into this PR

**M1 done when:** one authenticated top-up, fake provider, ledger posting, retry never posts twice.

---

## After M1 (later)

### M2 — P2P

- [ ] `POST /transfers` (Dr sender, Cr receiver)
- [ ] Same idempotency + ownership checks
- [ ] Insufficient funds → reject, no journal

### M3 — Withdraw

- [ ] `POST /withdrawals` (Dr wallet, Cr clearing) after fake payout
- [ ] Same PROCESSING → provider → SUCCEEDED pattern

### M4 — Demo polish

- [ ] `GET /wallets/me` (balance from ledger, not a stored column)
- [ ] OpenAPI examples for top-up
- [ ] README: how to run the demo

---

## Do not do until much later

- [ ] Second provider / routing
- [ ] Webhooks
- [ ] Outbox / Kafka
- [ ] Multi-currency / FX
- [ ] Split into two deployable services
- [ ] Real Stripe / JWT / KYC
