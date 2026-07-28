package com.doubleledger.ledger.service;

import com.doubleledger.ledger.audit.ForensicAuditService;
import com.doubleledger.ledger.dto.PostTransactionRequest;
import com.doubleledger.ledger.dto.TransactionLegDto;
import com.doubleledger.ledger.persistence.PostgresAdvisoryLockService;
import com.doubleledger.ledger.repository.AccountRepository;
import com.doubleledger.ledger.repository.JournalEntryRepository;
import com.doubleledger.ledger.repository.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerPostingServiceValidationTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private JournalEntryRepository journalEntryRepository;
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    @Mock
    private AccountBalanceService accountBalanceService;
    @Mock
    private ForensicAuditService forensicAuditService;
    @Mock
    private PostgresAdvisoryLockService advisoryLockService;

    @InjectMocks
    private LedgerPostingService postingService;

    private UUID idempotencyKey;
    private UUID accountA;
    private UUID accountB;

    @BeforeEach
    void setUp() {
        idempotencyKey = UUID.randomUUID();
        accountA = UUID.randomUUID();
        accountB = UUID.randomUUID();
    }

    private void stubNoExistingPosting() {
        when(journalEntryRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
    }

    @Test
    void postTransaction_unbalancedLegs_throwsBeforePersist() {
        stubNoExistingPosting();
        PostTransactionRequest request = new PostTransactionRequest();
        request.setIdempotencyKey(idempotencyKey);
        request.setLegs(List.of(
                new TransactionLegDto(accountA, 500, "DEBIT"),
                new TransactionLegDto(accountB, 300, "CREDIT")
        ));

        assertThatThrownBy(() -> postingService.postTransaction(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unbalanced Transaction");

        verify(journalEntryRepository, never()).save(any());
    }

    @Test
    void postTransaction_singleLeg_throwsBeforePersist() {
        stubNoExistingPosting();
        PostTransactionRequest request = new PostTransactionRequest();
        request.setIdempotencyKey(idempotencyKey);
        request.setLegs(List.of(new TransactionLegDto(accountA, 500, "DEBIT")));

        assertThatThrownBy(() -> postingService.postTransaction(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2 legs");

        verify(journalEntryRepository, never()).save(any());
    }

    @Test
    void postTransaction_invalidDirection_throwsBeforePersist() {
        stubNoExistingPosting();
        PostTransactionRequest request = new PostTransactionRequest();
        request.setIdempotencyKey(idempotencyKey);
        request.setLegs(List.of(
                new TransactionLegDto(accountA, 500, "DEBIT"),
                new TransactionLegDto(accountB, 500, "SIDEWAYS")
        ));

        assertThatThrownBy(() -> postingService.postTransaction(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid leg direction");

        verify(journalEntryRepository, never()).save(any());
    }

    @Test
    void postTransaction_missingIdempotencyKey_throwsBeforePersist() {
        PostTransactionRequest request = new PostTransactionRequest();
        request.setLegs(List.of(
                new TransactionLegDto(accountA, 500, "DEBIT"),
                new TransactionLegDto(accountB, 500, "CREDIT")
        ));

        assertThatThrownBy(() -> postingService.postTransaction(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Idempotency key is required");

        verify(journalEntryRepository, never()).save(any());
    }
}
