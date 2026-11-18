-- M1: customer wallets + top-ups. Money still lives on ledger accounts.
-- Wallet is the product mapping (who owns which account). Do not store a balance here.

-- Demo ledger accounts (fixed UUIDs so Java seed/tests can reference them)
--   Provider Clearing = asset  (debit on successful top-up)
--   Demo user wallet  = liability (credit on successful top-up)
-- Journal: Dr Provider Clearing / Cr User Wallet

INSERT INTO accounts (
    id, name, account_type, normal_balance, currency,
    allow_overdraft, overdraft_limit_minor_units, status
) VALUES
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
        'provider-clearing',
        'asset',
        'D',
        'USD',
        FALSE,
        0,
        'active'
    ),
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2',
        'demo-user-wallet',
        'liability',
        'C',
        'USD',
        FALSE,
        0,
        'active'
    );

CREATE TABLE wallets (
    id UUID PRIMARY KEY,
    user_id TEXT NOT NULL,
    ledger_account_id UUID NOT NULL UNIQUE REFERENCES accounts (id),
    currency CHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT wallets_currency_usd CHECK (currency = 'USD'),
    CONSTRAINT wallets_user_currency_unique UNIQUE (user_id, currency)
);

CREATE INDEX idx_wallets_user_id ON wallets (user_id);

INSERT INTO wallets (id, user_id, ledger_account_id, currency)
VALUES (
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1',
    'demo-user',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2',
    'USD'
);

CREATE TABLE topups (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL REFERENCES wallets (id),
    amount_minor_units BIGINT NOT NULL
        CHECK (amount_minor_units > 0),
    currency CHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('PROCESSING', 'SUCCEEDED', 'FAILED')),
    idempotency_key TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    provider_ref TEXT,
    journal_entry_id UUID REFERENCES journal_entries (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT topups_currency_usd CHECK (currency = 'USD'),
    CONSTRAINT topups_idempotency_key_unique UNIQUE (idempotency_key),
    CONSTRAINT topups_succeeded_has_journal
        CHECK (status <> 'SUCCEEDED' OR journal_entry_id IS NOT NULL),
    CONSTRAINT topups_failed_has_no_journal
        CHECK (status <> 'FAILED' OR journal_entry_id IS NULL)
);

CREATE INDEX idx_topups_wallet_id ON topups (wallet_id);
CREATE INDEX idx_topups_status ON topups (status);

CREATE TRIGGER trg_topups_updated_at
    BEFORE UPDATE ON topups
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
