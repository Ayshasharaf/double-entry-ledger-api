# DoubleLedger

A learning project: a Java 21 and Spring Boot API for double-entry accounting, backed by PostgreSQL. It supports balanced journal entries, reversals, idempotent requests, and a demo wallet top-up workflow.

> **Scope:** This is a local learning project, not a production payment processor or banking system. It has no authentication or tenant isolation.

## What it demonstrates

- Balanced, multi-leg ledger transactions
- Derived account balances and immutable postings
- Transaction reversals instead of editing history
- Idempotent requests using SHA-256 request hashing
- PostgreSQL advisory locks and account locking for concurrency
- Flyway migrations and PostgreSQL integration tests
- A demo wallet top-up flow using a fake payment provider

## Run locally

**Requirements:** Java 21+, Docker, and Docker Compose.

```bash
docker compose up --build
```

When the application starts, open [Swagger UI](http://localhost:8080/swagger-ui.html) to explore the API.

> Confirm the Compose file’s location and keep this as the single, correct startup command. The current README gives both a repository-root command and a `cd ledger` command.

## Example workflow

1. Create two accounts.
2. Post a balanced transaction with an `Idempotency-Key`.
3. Read the resulting balances.
4. Retry the request with the same key to see the idempotent replay.
5. Reverse a posted transaction.

Amounts are integers in minor units—for example, `500` means `$5.00` USD. The first successful posting returns `201 Created`; an exact retry returns `200 OK`.

## Architecture

The project is one Spring Boot application with two parts:

- **Ledger core:** accounts, journal entries, posting, balances, reversals, and idempotency.
- **Wallet demo:** wallet ownership checks and top-up orchestration through a fake provider.

PostgreSQL stores the ledger and wallet data. Flyway manages schema migrations, and database constraints provide a backstop for important accounting invariants.

## Tests

Run the test suite from the directory containing `mvnw`:

```bash
./mvnw clean test
```

Integration tests use Testcontainers and require Docker. Update this section with the current verified test result; avoid keeping a hardcoded test count unless you recheck it regularly.

## GitHub Actions: CI and image delivery

The workflow in `.github/workflows/ci.yml` runs the Maven test suite on pushes to `main` and `feature/**`, and on pull requests targeting `main`. It provides continuous integration (CI) only; it does not build/publish a release image or deploy the application. Deployment can be added later once a hosting target is selected.

This is CI plus container-image delivery, **not deployment of a running application**. Hosting still needs to be selected and configured with a PostgreSQL database and datasource credentials. The local database credentials are for development only.

## Limitations

This project is for learning and local experimentation. It does not include production authentication, multi-tenant isolation, real payment processing, or deployment automation. See [Limitations](#limitations) for more details.

## Learn more

- [Architecture overview](#architecture-overview)
- [API reference](#api-reference)
- [Testing](#testing)
- [Limitations](#limitations)
- [Guided codebase study](./CODEBASE_LEARNING.md)