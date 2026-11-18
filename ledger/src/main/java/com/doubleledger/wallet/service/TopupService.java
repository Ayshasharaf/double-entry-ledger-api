package com.doubleledger.wallet.service;

import com.doubleledger.ledger.dto.PostTransactionRequest;
import com.doubleledger.ledger.dto.TransactionLegDto;
import com.doubleledger.ledger.exception.IdempotencyConflictException;
import com.doubleledger.ledger.model.JournalEntry;
import com.doubleledger.ledger.persistence.PostgresAdvisoryLockService;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.doubleledger.ledger.service.LedgerPostingService;
import com.doubleledger.wallet.model.Topup;
import com.doubleledger.wallet.model.TopupStatus;
import com.doubleledger.wallet.model.Wallet;
import com.doubleledger.wallet.provider.ChargeResult;
import com.doubleledger.wallet.provider.PaymentProvider;
import com.doubleledger.wallet.repository.TopupRepository;
import com.doubleledger.wallet.repository.WalletRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


@Service
public class TopupService {
    private static final String CURRENCY = "USD";
    private static final UUID PROVIDER_CLEARING_ACCOUNT_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");

    private final WalletRepository walletRepository;
    private final TopupRepository topupRepository;
    private final PaymentProvider paymentProvider;
    private final LedgerPostingService ledgerPostingService;
    private final PostgresAdvisoryLockService advisoryLockService;
    private final TransactionTemplate transactionTemplate;

    public TopupService(WalletRepository walletRepository,
                        TopupRepository topupRepository,
                        PaymentProvider paymentProvider,
                        LedgerPostingService ledgerPostingService,
                        PostgresAdvisoryLockService advisoryLockService,
                        PlatformTransactionManager transactionManager) {
        this.walletRepository = walletRepository;
        this.topupRepository = topupRepository;
        this.paymentProvider = paymentProvider;
        this.ledgerPostingService = ledgerPostingService;
        this.advisoryLockService = advisoryLockService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public Topup createTopup(UUID walletId, String userId, long amountMinorUnits, String idempotencyKey) {
        validateRequest(walletId, userId, amountMinorUnits, idempotencyKey);
        String requestHash = hashRequest(walletId, amountMinorUnits);

        // Claim the key and commit PROCESSING before making the external provider call.
        TopupClaim claim = transactionTemplate.execute(status -> {
            // Serialize simultaneous requests that use this top-up idempotency key.
            advisoryLockService.acquireTransactionLock("wallet-topup:" + idempotencyKey);

            Wallet wallet = walletRepository.findById(walletId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found."));
            if (!wallet.getUserId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Wallet does not belong to this user.");
            }

            Optional<Topup> existing = topupRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                Topup topup = existing.get();
                // A key can only replay the same request; PROCESSING requests may resume.
                if (!topup.getRequestHash().equals(requestHash)) {
                    throw new IdempotencyConflictException(
                            "Idempotency key was already used with a different top-up request.");
                }
                return new TopupClaim(topup, topup.getStatus() == TopupStatus.PROCESSING);
            }

            Topup topup = new Topup();
            topup.setId(UUID.randomUUID());
            topup.setWalletId(walletId);
            topup.setAmountMinorUnits(amountMinorUnits);
            topup.setCurrency(CURRENCY);
            topup.setStatus(TopupStatus.PROCESSING);
            topup.setIdempotencyKey(idempotencyKey);
            topup.setRequestHash(requestHash);
            return new TopupClaim(topupRepository.save(topup), true);
        });

        if (!claim.shouldProcess()) {
            return claim.topup();
        }

        // The provider key protects the external charge from duplicate retries.
        ChargeResult chargeResult = paymentProvider.charge(amountMinorUnits, CURRENCY, idempotencyKey);
        if (!chargeResult.succeeded()) {
            return transactionTemplate.execute(status -> {
                Topup topup = findTopup(claim.topup().getId());
                topup.setStatus(TopupStatus.FAILED);
                topup.setUpdatedAt(OffsetDateTime.now());
                return topupRepository.save(topup);
            });
        }

        transactionTemplate.executeWithoutResult(status -> {
            Topup topup = findTopup(claim.topup().getId());
            topup.setProviderRef(chargeResult.providerRef());
            topup.setUpdatedAt(OffsetDateTime.now());
            topupRepository.save(topup);
        });

        // Post the journal and mark the top-up successful in one database transaction.
        return transactionTemplate.execute(status -> {
            Topup topup = findTopup(claim.topup().getId());
            Wallet wallet = walletRepository.findById(topup.getWalletId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found."));

            PostTransactionRequest postingRequest = new PostTransactionRequest();
            // This derived key is independent from the provider key and deduplicates ledger postings.
            postingRequest.setIdempotencyKey(UUID.nameUUIDFromBytes(
                    ("wallet-topup:" + idempotencyKey).getBytes(StandardCharsets.UTF_8)));
            postingRequest.setDescription("Wallet top-up " + topup.getId());
            postingRequest.setMetadata(Map.of(
                    "topupId", topup.getId().toString(),
                    "walletId", wallet.getId().toString(),
                    "providerRef", chargeResult.providerRef() == null ? "" : chargeResult.providerRef()));
            postingRequest.setLegs(List.of(
                    new TransactionLegDto(PROVIDER_CLEARING_ACCOUNT_ID, amountMinorUnits, "DEBIT"),
                    new TransactionLegDto(wallet.getLedgerAccountId(), amountMinorUnits, "CREDIT")));

            JournalEntry journalEntry = ledgerPostingService.postTransaction(postingRequest);
            topup.setJournalEntryId(journalEntry.getId());
            topup.setStatus(TopupStatus.SUCCEEDED);
            topup.setUpdatedAt(OffsetDateTime.now());
            return topupRepository.save(topup);
        });
    }

    private Topup findTopup(UUID topupId) {
        return topupRepository.findById(topupId)
                .orElseThrow(() -> new IllegalStateException("Top-up record disappeared."));
    }

    private static void validateRequest(UUID walletId,
                                        String userId,
                                        long amountMinorUnits,
                                        String idempotencyKey) {
        if (walletId == null || userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Wallet and user are required.");
        }
        if (amountMinorUnits <= 0) {
            throw new IllegalArgumentException("Top-up amount must be positive.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required.");
        }
    }

    private static String hashRequest(UUID walletId, long amountMinorUnits) {
        String request = walletId + ":" + amountMinorUnits + ":" + CURRENCY;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(request.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }

    private record TopupClaim(Topup topup, boolean shouldProcess) {}
    


}

