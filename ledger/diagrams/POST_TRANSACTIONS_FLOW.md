# POST `/transactions` — flow (paste this, not draw.io XML)

Your `%3CmxGraphModel%3E...` blob is **draw.io internal format** (~50KB). Portfolios and GitHub cannot use it.

**Paste one of the Mermaid blocks below** into:

- GitHub README (renders automatically)
- [mermaid.live](https://mermaid.live) → **Export PNG/SVG** for portfolio sites
- draw.io → **+ → Advanced → Mermaid** (paste source, not XML)

---

## Portfolio version (2 small diagrams)

### 1 — Request & idempotency

```mermaid
flowchart TD
    A([POST /transactions]) --> B{Idempotency-Key valid UUID<br/>and matches body?}
    B -->|No| E400[400 Bad Request]
    B -->|Yes| C[SHA-256 request hash]
    C --> D[API advisory lock]
    D --> F{Key in idempotency_keys?}
    F -->|Yes, same hash| G[200 OK · Idempotent-Replayed]
    F -->|Yes, different hash| H[409 Conflict]
    F -->|No| I[Continue to posting →]

    style G fill:#d4edda
    style H fill:#f8d7da
    style E400 fill:#f8d7da
```

### 2 — Posting (single DB transaction)

```mermaid
flowchart TD
    I([postTransaction]) --> J[Domain idempotency lock]
    J --> K{Validate legs, zero-sum,<br/>accounts, currency, overdraft}
    K -->|Fail| E400[400 · nothing committed]
    K -->|Frozen/closed| E500[500 · rollback]
    K -->|OK| L[SELECT accounts FOR UPDATE<br/>sorted by ID]
    L --> M[Insert journal + ledger legs]
    M --> N{Deferred trigger:<br/>legs sum to 0 at COMMIT?}
    N -->|No| RB[Rollback all writes]
    N -->|Yes| O[Save idempotency_keys]
    O --> P[201 Created]

    style P fill:#d4edda
    style E400 fill:#f8d7da
    style E500 fill:#f8d7da
    style RB fill:#f8d7da
```

---

## Full detail (one diagram)

Same as your draw.io chart. Source file: `post-transactions-full.mmd`

```mermaid
flowchart TD
    START([Client POST /transactions]) --> H1{Idempotency-Key<br/>header present?}
    H1 -->|No| R400a[400 Bad Request]
    H1 -->|Yes| H2{Valid UUID<br/>≤ 255 chars?}
    H2 -->|No| R400b[400 Bad Request]
    H2 -->|Yes| H3{Body key matches<br/>header?}
    H3 -->|No| R400c[400 Bad Request]
    H3 -->|Yes| HASH[Compute SHA-256 hash]

    HASH --> LOCK1[API advisory lock]
    LOCK1 --> CACHE{Key in<br/>idempotency_keys?}

    CACHE -->|Yes, hash match| R200[200 OK<br/>Idempotent-Replayed: true]
    CACHE -->|Yes, hash mismatch| R409[409 Conflict]
    CACHE -->|No| POST[LedgerPostingService.postTransaction]

    POST --> LOCK0[Domain idempotency advisory lock]
    LOCK0 --> V1{≥ 2 legs?}
    V1 -->|No| R400d[400 Bad Request]
    V1 -->|Yes| V2{Debits = Credits?}
    V2 -->|No| R400e[400 Bad Request]
    V2 -->|Yes| LOCK2[Lock accounts<br/>sorted by ID<br/>SELECT FOR UPDATE]
    LOCK2 --> V3{All accounts exist?}
    V3 -->|No| R400f[400 Bad Request]
    V3 -->|Yes| V4{Same currency<br/>on all legs?}
    V4 -->|No| R400g[400 Bad Request]
    V4 -->|Yes| V5{Frozen or closed<br/>account?}
    V5 -->|Yes| R500h[500 Internal Server Error]
    V5 -->|No| V6{Overdraft OK?}
    V6 -->|No| R400i[400 Bad Request]
    V6 -->|Yes| WRITE[Write journal + ledger legs]
    WRITE --> TRIGGER[DB trigger: zero-sum at COMMIT]
    TRIGGER -->|Fail| ROLLBACK[Rollback entire transaction]
    TRIGGER -->|Pass| CACHE2[Save idempotency_keys row]
    CACHE2 --> R201[201 Created]

    style R201 fill:#d4edda
    style R200 fill:#d4edda
    style R409 fill:#f8d7da
    style R400a fill:#f8d7da
```

---

## Immutability note (from your diagram)

Ledger entries are **append-only** — no `UPDATE` or `DELETE` on posted legs; corrections use **reversals**.
