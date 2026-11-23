# Double Entry Ledger API

[![CI](https://github.com/Ayshasharaf/double-entry-ledger-api/actions/workflows/ci.yml/badge.svg)](https://github.com/Ayshasharaf/double-entry-ledger-api/actions/workflows/ci.yml)

A Java 21 and Spring Boot API for learning and experimenting with double-entry accounting. The application records balanced, immutable journal postings in PostgreSQL, supports reversals and idempotent requests, and demonstrates a wallet top-up workflow with a fake payment provider.

> **Project scope:** This is a local learning project, not a production payment processor or banking system. It does not include authentication, tenant isolation, real payment processing, or deployment automation.

## Key features

- Balanced journal entries with multiple debit and credit legs
- Immutable postings and derived account balances
- Reversals that add correction entries instead of changing history
- Idempotency through SHA-256 request hashing and `Idempotency-Key`
- PostgreSQL advisory locks and database constraints for concurrency and integrity
- Flyway schema migrations
- PostgreSQL integration tests with Testcontainers
- Demo wallet top-up orchestration using a fake provider
- Swagger/OpenAPI documentation

## Technology

- Java 21
- Spring Boot 4
- Spring Data JPA
- PostgreSQL 16
- Flyway
- Maven
- Docker and Docker Compose
- Testcontainers
- SpringDoc OpenAPI

## Run locally

### Prerequisites

- Docker with Docker Compose
- Java 21 and Maven only if you want to run the application or tests outside Docker

### Start the API and PostgreSQL

From the repository root:

```bash
cd ledger
docker compose up --build
```

The API will be available at `http://localhost:8080`, and Swagger UI will be available at:

- `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`

PostgreSQL is published on host port `5433`.

To stop the services:

```bash
docker compose down
```

To stop the services and delete the local database volume:

```bash
docker compose down -v
```

## Example workflow

1. Create two accounts.
2. Post a balanced transaction with an `Idempotency-Key` header.
3. Read the resulting account balances.
4. Retry the same request with the same key to verify the idempotent replay.
5. Reverse the posted transaction.

Amounts are integer values in minor units. For example, `500` represents $5.00 in USD if USD is used by the caller as a two-decimal currency. The first successful posting returns `201 Created`; an exact retry returns `200 OK`.

## Architecture

The application is organized around two areas:

- **Ledger core** — accounts, journal entries, postings, balances, reversals, and idempotency
- **Wallet demo** — wallet ownership checks and top-up orchestration through a fake payment provider

PostgreSQL stores all ledger and wallet state. Flyway manages schema changes, JPA handles persistence, and database constraints provide a final backstop for accounting invariants.

For the design background and implementation phases, see [Phase 0: Payment Intent](./docs/phase-0-payment-intent.md).

## Tests

Run the Maven test suite from the application directory:

```bash
cd ledger
./mvnw clean test
```

The integration tests use Testcontainers and therefore require Docker.

## CI

The [CI workflow](./.github/workflows/ci.yml) uses Java 21 and Maven to run `./mvnw clean test` on:

- pushes to `main` and `feature/**`
- pull requests targeting `main`

CI currently validates tests only. It does not deploy an application or provision infrastructure.

## Repository structure

```text
.
├── ledger/                 Spring Boot application and Maven project
│   ├── src/main/java       Application source code
│   ├── src/main/resources  Configuration, Flyway migrations, and logging
│   └── docker-compose.yml  Local API and PostgreSQL services
├── docs/                   Design and implementation notes
└── docker-compose.yml      PostgreSQL-only local setup
```
