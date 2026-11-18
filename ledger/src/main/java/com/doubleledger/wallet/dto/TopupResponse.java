package com.doubleledger.wallet.dto;

import com.doubleledger.wallet.model.Topup;
import com.doubleledger.wallet.model.TopupStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TopupResponse(
        UUID id,
        UUID walletId,
        Long amountMinorUnits,
        String currency,
        TopupStatus status,
        String providerRef,
        UUID journalEntryId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static TopupResponse from(Topup topup) {
        return new TopupResponse(
                topup.getId(),
                topup.getWalletId(),
                topup.getAmountMinorUnits(),
                topup.getCurrency(),
                topup.getStatus(),
                topup.getProviderRef(),
                topup.getJournalEntryId(),
                topup.getCreatedAt(),
                topup.getUpdatedAt());
    }
}