package com.doubleledger.ledger;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.doubleledger.ledger.model.Account;
import com.doubleledger.ledger.repository.JournalEntryRepository;
import com.doubleledger.ledger.repository.LedgerEntryRepository;
import com.doubleledger.ledger.support.PostgresIntegrationTestSupport;
import com.doubleledger.wallet.dto.TopupResponse;
import com.doubleledger.wallet.model.TopupStatus;
import com.doubleledger.wallet.model.Wallet;
import com.doubleledger.wallet.provider.FakePaymentProvider;
import com.doubleledger.wallet.repository.TopupRepository;
import com.doubleledger.wallet.repository.WalletRepository;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient 
class TopupIntegrationTest extends PostgresIntegrationTestSupport{
        private static final UUID DEMO_WALLET_ID =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1");
        private static final UUID PROVIDER_CLEARING_ACCOUNT_ID =
                        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    private static final String DEMO_USER_ID = "demo-user";

    @Autowired 
    private RestTestClient restTestClient;

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
    void loadDemoWallet() {
        wallet = walletRepository.findById(DEMO_WALLET_ID).orElseThrow();
        walletAccount = accountRepository.findById(wallet.getLedgerAccountId()).orElseThrow();
    }

    private RestTestClient.ResponseSpec postTopup(
        UUID walletId,
        String userId,
        String idempotencyKey,
        long amountMinorUnits) {
        return restTestClient.post()
                .uri("/api/v1/wallets/{walletId}/topups", walletId)
                .header("X-User-Id", userId)
                .header("Idempotency-Key", idempotencyKey)
                .body(Map.of("amountMinorUnits", amountMinorUnits))
                .exchange();
    }

    @Test 
    void successfulTopup() {
        long amount = 1000L;
        long startingBalance= balanceOf(walletAccount);
        long topupsBefore= topupRepository.count();
        long journalsBefore= journalEntryRepository.count();
        String key = UUID.randomUUID().toString();

        TopupResponse response =  postTopup(wallet.getId(), DEMO_USER_ID, key, amount)
                .expectStatus().isCreated()
                .expectBody(TopupResponse.class)
                .returnResult()
                .getResponseBody();  

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(TopupStatus.SUCCEEDED);

        assertThat(response.amountMinorUnits()).isEqualTo(amount);
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.walletId()).isEqualTo(wallet.getId());
        assertThat(response.providerRef()).isEqualTo("fake_" + key);
        assertThat(response.journalEntryId()).isNotNull();

        assertThat(topupRepository.count()).isEqualTo(topupsBefore + 1);
        assertThat(journalEntryRepository.count()).isEqualTo(journalsBefore + 1);
        assertThat(balanceOf(walletAccount)).isEqualTo(startingBalance + amount);

                var entries = ledgerEntryRepository
                                .findByJournalEntryIdOrderByCreatedAtAsc(response.journalEntryId());
                assertThat(entries).hasSize(2);
                assertThat(entries).anySatisfy(entry -> {
                        assertThat(entry.getAccountId()).isEqualTo(PROVIDER_CLEARING_ACCOUNT_ID);
                        assertThat(entry.getAmountMinorUnits()).isEqualTo(-amount);
                });
                assertThat(entries).anySatisfy(entry -> {
                        assertThat(entry.getAccountId()).isEqualTo(wallet.getLedgerAccountId());
                        assertThat(entry.getAmountMinorUnits()).isEqualTo(amount);
                });

    }
    @Test 
    void topupIdempotency(){
        
        long amount = 1000L;
        long startingBalance= balanceOf(walletAccount);
        long topupsBefore= topupRepository.count();
        long journalsBefore= journalEntryRepository.count();
        String key = UUID.randomUUID().toString();

        TopupResponse response1 =  postTopup(wallet.getId(), DEMO_USER_ID, key, amount)
                .expectStatus().isCreated()
                .expectBody(TopupResponse.class)
                .returnResult()
                .getResponseBody();  

        assertThat(response1).isNotNull();
        assertThat(response1.status()).isEqualTo(TopupStatus.SUCCEEDED);

        TopupResponse response2 =  postTopup(wallet.getId(), DEMO_USER_ID, key, amount)
                .expectStatus().isCreated()
                .expectBody(TopupResponse.class)
                .returnResult()
                .getResponseBody();  

        assertThat(response2).isNotNull();
        assertThat(response2.status()).isEqualTo(TopupStatus.SUCCEEDED);

        assertThat(response1.id()).isEqualTo(response2.id());
        assertThat(response1.journalEntryId()).isEqualTo(response2.journalEntryId());

        assertThat(topupRepository.count()).isEqualTo(topupsBefore + 1);
        assertThat(journalEntryRepository.count()).isEqualTo(journalsBefore + 1);
        assertThat(balanceOf(walletAccount)).isEqualTo(startingBalance + amount);
        
    }

    @Test 
    void sameIdempotencyKeyWithdifferentAmount(){
        long amount = 1000L;
        long amount2 = 2000L;
        long startingBalance= balanceOf(walletAccount);
        long topupsBefore= topupRepository.count();
        long journalsBefore= journalEntryRepository.count();
        String key = UUID.randomUUID().toString();

        TopupResponse response1 =  postTopup(wallet.getId(), DEMO_USER_ID, key, amount)
                .expectStatus().isCreated()
                .expectBody(TopupResponse.class)
                .returnResult()
                .getResponseBody();  

        assertThat(response1).isNotNull();
        assertThat(response1.status()).isEqualTo(TopupStatus.SUCCEEDED);

        postTopup(wallet.getId(), DEMO_USER_ID, key, amount2)
                .expectStatus().isEqualTo(409);

        var savedTopup = topupRepository.findByIdempotencyKey(key).orElseThrow();
        assertThat(savedTopup.getAmountMinorUnits()).isEqualTo(amount);
        assertThat(savedTopup.getStatus()).isEqualTo(TopupStatus.SUCCEEDED);
        assertThat(savedTopup.getJournalEntryId()).isEqualTo(response1.journalEntryId());

        assertThat(topupRepository.count()).isEqualTo(topupsBefore + 1);
        assertThat(journalEntryRepository.count()).isEqualTo(journalsBefore + 1);
        assertThat(balanceOf(walletAccount)).isEqualTo(startingBalance + amount);
    }

    @Test
    void providerDecline_marksTopupFailedWithoutJournalOrBalanceChange() {
        long startingBalance = balanceOf(walletAccount);
        long topupsBefore = topupRepository.count();
        long journalsBefore = journalEntryRepository.count();
        String key = UUID.randomUUID().toString();

        TopupResponse response = postTopup(
                wallet.getId(), DEMO_USER_ID, key,
                FakePaymentProvider.FAIL_AMOUNT_MINOR_UNITS)
                .expectStatus().isCreated()
                .expectBody(TopupResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(TopupStatus.FAILED);
        assertThat(response.journalEntryId()).isNull();
        assertThat(topupRepository.count()).isEqualTo(topupsBefore + 1);
        assertThat(journalEntryRepository.count()).isEqualTo(journalsBefore);
        assertThat(balanceOf(walletAccount)).isEqualTo(startingBalance);
    }

    @Test
    void wrongUser_returnsForbiddenWithoutCreatingTopup() {
        long topupsBefore = topupRepository.count();
        long journalsBefore = journalEntryRepository.count();

        postTopup(wallet.getId(), "another-user", UUID.randomUUID().toString(), 1_000L)
                .expectStatus().isForbidden();

        assertThat(topupRepository.count()).isEqualTo(topupsBefore);
        assertThat(journalEntryRepository.count()).isEqualTo(journalsBefore);
    }

    @Test
    void unknownWallet_returnsNotFoundWithoutCreatingTopup() {
        long topupsBefore = topupRepository.count();
        long journalsBefore = journalEntryRepository.count();

        postTopup(UUID.randomUUID(), DEMO_USER_ID, UUID.randomUUID().toString(), 1_000L)
                .expectStatus().isNotFound();

        assertThat(topupRepository.count()).isEqualTo(topupsBefore);
        assertThat(journalEntryRepository.count()).isEqualTo(journalsBefore);
    }

    @Test
    void zeroAmount_returnsBadRequestWithoutCreatingTopup() {
        long topupsBefore = topupRepository.count();
        long journalsBefore = journalEntryRepository.count();

        postTopup(wallet.getId(), DEMO_USER_ID, UUID.randomUUID().toString(), 0L)
                .expectStatus().isBadRequest();

        assertThat(topupRepository.count()).isEqualTo(topupsBefore);
        assertThat(journalEntryRepository.count()).isEqualTo(journalsBefore);
    }
    
    
}