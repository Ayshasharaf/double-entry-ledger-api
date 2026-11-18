package com.doubleledger.ledger;

import com.doubleledger.ledger.model.Account;
import com.doubleledger.ledger.model.JournalEntry;
import com.doubleledger.ledger.model.LedgerEntry;
import com.doubleledger.ledger.repository.JournalEntryRepository;
import com.doubleledger.ledger.repository.LedgerEntryRepository;
import com.doubleledger.ledger.support.PostgresIntegrationTestSupport;
import com.doubleledger.wallet.dto.TopupResponse;
import com.doubleledger.wallet.model.TopupStatus;
import com.doubleledger.wallet.model.Wallet;
import com.doubleledger.wallet.provider.FakePaymentProvider;
import com.doubleledger.wallet.repository.TopupRepository;
import com.doubleledger.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class TopupIntegrationTest extends PostgresIntegrationTestSupport {

    private static final UUID DEMO_WALLET_ID =
        UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1");
    private static final UUID PROVIDER_CLEARING_ACCOUNT_ID =
        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    private static final String DEMO_USER_ID = "demo-user";

    @Autowired
    private RestTestClient restClient;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TopupRepository topupRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    private Wallet wallet;
    private Account walletAccount;

    @BeforeEach
    void loadSeededWallet() {
    wallet = walletRepository.findById(DEMO_WALLET_ID).orElseThrow();
    walletAccount = accountRepository.findById(wallet.getLedgerAccountId()).orElseThrow();
    }

    @Test
    void successfulTopup_postsBalancedJournalAndIncreasesWalletBalance() {
    long amount = 500L;
    long startingBalance = balanceOf(walletAccount);
    long topupCountBefore = topupRepository.count();
    long journalCountBefore = journalEntryRepository.count();
    String idempotencyKey = UUID.randomUUID().toString();

    TopupResponse response = postTopup(wallet.getId(), DEMO_USER_ID, idempotencyKey, amount)
        .expectStatus().isCreated()
        .expectBody(TopupResponse.class)
        .returnResult()
        .getResponseBody();

    assertThat(response).isNotNull();
    assertThat(response.status()).isEqualTo(TopupStatus.SUCCEEDED);
    assertThat(response.amountMinorUnits()).isEqualTo(amount);
    assertThat(response.currency()).isEqualTo("USD");
    assertThat(response.providerRef()).isEqualTo("fake_" + idempotencyKey);
    assertThat(response.journalEntryId()).isNotNull();
    assertThat(topupRepository.count()).isEqualTo(topupCountBefore + 1);
    assertThat(journalEntryRepository.count()).isEqualTo(journalCountBefore + 1);
    assertThat(balanceOf(walletAccount)).isEqualTo(startingBalance + amount);

    JournalEntry journal = journalEntryRepository.findById(response.journalEntryId()).orElseThrow();
    assertThat(journal.getIdempotencyKey()).isEqualTo(ledgerIdempotencyKey(idempotencyKey));

    List<LedgerEntry> entries = ledgerEntryRepository
        .findByJournalEntryIdOrderByCreatedAtAsc(journal.getId());
    assertThat(entries).hasSize(2);
    assertEntry(entries, PROVIDER_CLEARING_ACCOUNT_ID, -amount);
    assertEntry(entries, wallet.getLedgerAccountId(), amount);
    }

    @Test
    void retryOfSucceededTopup_returnsSameTopupAndDoesNotPostAgain() {
    long amount = 750L;
    long startingBalance = balanceOf(walletAccount);
    long topupCountBefore = topupRepository.count();
    long journalCountBefore = journalEntryRepository.count();
    String idempotencyKey = UUID.randomUUID().toString();

    TopupResponse first = postTopup(wallet.getId(), DEMO_USER_ID, idempotencyKey, amount)
        .expectStatus().isCreated()
        .expectBody(TopupResponse.class)
        .returnResult()
        .getResponseBody();
    TopupResponse retry = postTopup(wallet.getId(), DEMO_USER_ID, idempotencyKey, amount)
        .expectStatus().isCreated()
        .expectBody(TopupResponse.class)
        .returnResult()
        .getResponseBody();

    assertThat(first).isNotNull();
    assertThat(retry).isNotNull();
    assertThat(retry.id()).isEqualTo(first.id());
    assertThat(retry.journalEntryId()).isEqualTo(first.journalEntryId());
    assertThat(topupRepository.count()).isEqualTo(topupCountBefore + 1);
    assertThat(journalEntryRepository.count()).isEqualTo(journalCountBefore + 1);
    assertThat(balanceOf(walletAccount)).isEqualTo(startingBalance + amount);
    }

    @Test
    void sameIdempotencyKeyWithDifferentAmount_returnsConflict() {
    String idempotencyKey = UUID.randomUUID().toString();
    long topupCountBefore = topupRepository.count();
    long journalCountBefore = journalEntryRepository.count();

    postTopup(wallet.getId(), DEMO_USER_ID, idempotencyKey, 500L)
        .expectStatus().isCreated();
    postTopup(wallet.getId(), DEMO_USER_ID, idempotencyKey, 900L)
        .expectStatus().isEqualTo(409);

    assertThat(topupRepository.count()).isEqualTo(topupCountBefore + 1);
    assertThat(journalEntryRepository.count()).isEqualTo(journalCountBefore + 1);
    }

    @Test
    void providerDecline_marksTopupFailedWithoutJournalOrBalanceChange() {
    long startingBalance = balanceOf(walletAccount);
    long journalCountBefore = journalEntryRepository.count();
    String idempotencyKey = UUID.randomUUID().toString();

    TopupResponse response = postTopup(
        wallet.getId(), DEMO_USER_ID, idempotencyKey,
        FakePaymentProvider.FAIL_AMOUNT_MINOR_UNITS)
        .expectStatus().isCreated()
        .expectBody(TopupResponse.class)
        .returnResult()
        .getResponseBody();

    assertThat(response).isNotNull();
    assertThat(response.status()).isEqualTo(TopupStatus.FAILED);
    assertThat(response.journalEntryId()).isNull();
    assertThat(journalEntryRepository.count()).isEqualTo(journalCountBefore);
    assertThat(balanceOf(walletAccount)).isEqualTo(startingBalance);
    }

    @Test
    void wrongUser_returnsForbiddenWithoutCreatingTopup() {
    long topupCountBefore = topupRepository.count();
    long journalCountBefore = journalEntryRepository.count();

    postTopup(wallet.getId(), "some-other-user", UUID.randomUUID().toString(), 500L)
        .expectStatus().isForbidden();

    assertThat(topupRepository.count()).isEqualTo(topupCountBefore);
    assertThat(journalEntryRepository.count()).isEqualTo(journalCountBefore);
    }

    @Test
    void unknownWallet_returnsNotFoundWithoutCreatingTopup() {
    long topupCountBefore = topupRepository.count();
    long journalCountBefore = journalEntryRepository.count();

    postTopup(UUID.randomUUID(), DEMO_USER_ID, UUID.randomUUID().toString(), 500L)
        .expectStatus().isNotFound();

    assertThat(topupRepository.count()).isEqualTo(topupCountBefore);
    assertThat(journalEntryRepository.count()).isEqualTo(journalCountBefore);
    }

    @Test
    void zeroAmount_returnsBadRequestWithoutCreatingTopup() {
    long topupCountBefore = topupRepository.count();

    postTopup(wallet.getId(), DEMO_USER_ID, UUID.randomUUID().toString(), 0L)
        .expectStatus().isBadRequest();

    assertThat(topupRepository.count()).isEqualTo(topupCountBefore);
    }

    private RestTestClient.ResponseSpec postTopup(UUID walletId,
                           String userId,
                           String idempotencyKey,
                           long amountMinorUnits) {
    return restClient.post()
        .uri("/api/v1/wallets/{walletId}/topups", walletId)
        .header("X-User-Id", userId)
        .header("Idempotency-Key", idempotencyKey)
        .body(Map.of("amountMinorUnits", amountMinorUnits))
        .exchange();
    }

    private static UUID ledgerIdempotencyKey(String topupKey) {
    return UUID.nameUUIDFromBytes(
        ("wallet-topup:" + topupKey).getBytes(StandardCharsets.UTF_8));
    }

    private static void assertEntry(List<LedgerEntry> entries, UUID accountId, long signedAmount) {
    assertThat(entries)
        .anySatisfy(entry -> {
            assertThat(entry.getAccountId()).isEqualTo(accountId);
            assertThat(entry.getAmountMinorUnits()).isEqualTo(signedAmount);
        });
    }
}
