package com.doubleledger.ledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * This class represents the exact JSON payload sent by a client
 * to create a new financial ledger account.
 */
@Schema(description = "Create a ledger account. Only name, accountType, normalBalance, and currency are required.")
public class CreateAccountRequest {

    @Schema(
            description = "Optional. If omitted, the server generates a UUID.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED,
            example = "550e8400-e29b-41d4-a716-446655440000"
    )
    private UUID id;

    @NotBlank
    @Schema(description = "Human-readable account name.", example = "demo-wallet")
    private String name;

    @NotBlank
    @Schema(
            description = "One of: asset, liability, equity, revenue, expense.",
            example = "asset"
    )
    private String accountType;

    @NotBlank
    @Schema(description = "Normal balance side: D (debit) or C (credit).", example = "D")
    private String normalBalance;

    @NotBlank
    @Schema(description = "ISO 4217 currency code.", example = "USD")
    private String currency;

    @Schema(description = "Whether the account may go below zero. Defaults to false if omitted.")
    private boolean allowOverdraft;

    @Schema(description = "Max overdraft in minor units when allowOverdraft is true. Defaults to 0 if omitted.")
    private long overdraftLimitMinorUnits;

    // --- Empty Constructor (Required by Jackson for JSON parsing) ---
    public CreateAccountRequest() {}

    // --- Getters & Setters ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }

    public String getNormalBalance() { return normalBalance; }
    public void setNormalBalance(String normalBalance) { this.normalBalance = normalBalance; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public boolean isAllowOverdraft() { return allowOverdraft; }
    public void setAllowOverdraft(boolean allowOverdraft) { this.allowOverdraft = allowOverdraft; }

    public long getOverdraftLimitMinorUnits() { return overdraftLimitMinorUnits; }
    public void setOverdraftLimitMinorUnits(long overdraftLimitMinorUnits) { this.overdraftLimitMinorUnits = overdraftLimitMinorUnits; }
}