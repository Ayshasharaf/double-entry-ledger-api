package com.doubleledger.wallet.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "ledger_account_id", nullable = false, unique = true)
    private UUID ledgerAccountId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public UUID getLedgerAccountId() {
        return ledgerAccountId;
    }

    public void setLedgerAccountId(UUID ledgerAccountId) {
        this.ledgerAccountId = ledgerAccountId;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
