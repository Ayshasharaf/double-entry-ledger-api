Create table wallets(
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    ledger_account_id UUID NOT NULL,
    currency VARCHAR(3) NOT NULL,
);
Create table topups(
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL,
    amount_minor_units BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    request_hash VARCHAR(255) NOT NULL,
    provider_ref VARCHAR(255) NOT NULL,
    journal_entry_id UUID NOT NULL,
);



