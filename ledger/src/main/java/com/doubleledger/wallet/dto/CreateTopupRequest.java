package com.doubleledger.wallet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateTopupRequest(
        @NotNull @Positive Long amountMinorUnits) {
}