package com.doubleledger.ledger.util;

import com.doubleledger.ledger.dto.PostTransactionRequest;
import com.doubleledger.ledger.dto.ReverseTransactionRequest;
import com.doubleledger.ledger.dto.TransactionLegDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestHasherTest {

    private RequestHasher hasher;

    @BeforeEach
    void setUp() {
        hasher = new RequestHasher(new ObjectMapper());
    }

    @Test
    void hashPostTransaction_sameBodyProducesSameHash() {
        PostTransactionRequest request = samplePostRequest();

        String first = hasher.hashPostTransaction(request);
        String second = hasher.hashPostTransaction(request);

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(64);
    }

    @Test
    void hashPostTransaction_differentAmountProducesDifferentHash() {
        PostTransactionRequest first = samplePostRequest();
        PostTransactionRequest second = samplePostRequest();
        second.getLegs().get(0).setAmountMinorUnits(999);

        assertThat(hasher.hashPostTransaction(first))
                .isNotEqualTo(hasher.hashPostTransaction(second));
    }

    @Test
    void hashPostTransaction_mapKeyOrderDoesNotChangeHash() {
        PostTransactionRequest request = samplePostRequest();
        request.setMetadata(Map.of("b", 2, "a", 1));

        PostTransactionRequest reordered = samplePostRequest();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("a", 1);
        metadata.put("b", 2);
        reordered.setMetadata(metadata);

        assertThat(hasher.hashPostTransaction(request))
                .isEqualTo(hasher.hashPostTransaction(reordered));
    }

    @Test
    void hashReverseTransaction_sameInputsProduceSameHash() {
        UUID journalEntryId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        ReverseTransactionRequest request = new ReverseTransactionRequest();
        request.setDescription("undo transfer");

        String first = hasher.hashReverseTransaction(journalEntryId, request);
        String second = hasher.hashReverseTransaction(journalEntryId, request);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void hashReverseTransaction_differentJournalEntryIdProducesDifferentHash() {
        ReverseTransactionRequest request = new ReverseTransactionRequest();
        request.setDescription("undo transfer");

        UUID firstId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        UUID secondId = UUID.fromString("660e8400-e29b-41d4-a716-446655440001");

        assertThat(hasher.hashReverseTransaction(firstId, request))
                .isNotEqualTo(hasher.hashReverseTransaction(secondId, request));
    }

    private static PostTransactionRequest samplePostRequest() {
        UUID walletId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID poolId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        PostTransactionRequest request = new PostTransactionRequest();
        request.setIdempotencyKey(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        request.setDescription("transfer to pool");
        request.setLegs(List.of(
                new TransactionLegDto(walletId, 500, "CREDIT"),
                new TransactionLegDto(poolId, 500, "DEBIT")
        ));
        return request;
    }
}
