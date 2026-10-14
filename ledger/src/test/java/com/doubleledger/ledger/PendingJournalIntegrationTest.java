package com.doubleledger.ledger;

import com.doubleledger.ledger.dto.JournalEntryResponse;
import com.doubleledger.ledger.dto.PostTransactionRequest;
import com.doubleledger.ledger.model.Account;
import com.doubleledger.ledger.model.JournalEntryStatus;
import com.doubleledger.ledger.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class PendingJournalIntegrationTest extends PostgresIntegrationTestSupport {
    @Autowired
    private RestTestClient restClient;

    private Account wallet;
    private Account pool;
    @BeforeEach
    void setUpAccounts() {
        wallet = createAssetAccount("pending-journal-wallet");
        pool = createAssetAccount("pending-journal-pool");
        fundAccount(wallet, 5_000L);
    }

@Test
void postTransaction_pending_savesHoldwithNoPostedAt() {
    UUID idempotencyKey = UUID.randomUUID();
    PostTransactionRequest request = buildTransfer(wallet.getId(), pool.getId(), 500L, idempotencyKey);
    request.setStatus(JournalEntryStatus.pending);


    JournalEntryResponse created = restClient.post()
    .uri("/api/v1/transactions")
    .header("Idempotency-Key", idempotencyKey.toString())
    .body(request)
    .exchange()
    .expectStatus().isCreated()
    .expectBody(JournalEntryResponse.class)
    .returnResult()
    .getResponseBody();

    assertThat(created.status()).isEqualTo("pending");
    assertThat(created.postedAt()).isNull();    
}
}