package com.doubleledger.ledger.service;

import com.doubleledger.ledger.model.Account;
import com.doubleledger.ledger.model.AccountType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccountBalanceServiceTest {

    private AccountBalanceService accountBalanceService;

    @BeforeEach
    void setUp() {
        accountBalanceService = new AccountBalanceService(null);
    }

    @Test
    void applySignedLeg_assetAccountCreditDecreasesBalance() {
        Account asset = assetAccount();

        long newBalance = accountBalanceService.applySignedLeg(asset, 10_000, 500);

        assertThat(newBalance).isEqualTo(9_500);
    }

    @Test
    void applySignedLeg_liabilityAccountCreditIncreasesBalance() {
        Account liability = liabilityAccount();

        long newBalance = accountBalanceService.applySignedLeg(liability, 0, 500);

        assertThat(newBalance).isEqualTo(500);
    }

    @Test
    void toEconomicBalance_assetUsesDebitNormalConvention() {
        assertThat(AccountBalanceService.toEconomicBalance(AccountType.asset, -500)).isEqualTo(500);
        assertThat(AccountBalanceService.toEconomicBalance(AccountType.asset, 500)).isEqualTo(-500);
    }

    @Test
    void toEconomicBalance_liabilityUsesCreditNormalConvention() {
        assertThat(AccountBalanceService.toEconomicBalance(AccountType.liability, 500)).isEqualTo(500);
        assertThat(AccountBalanceService.toEconomicBalance(AccountType.liability, -500)).isEqualTo(-500);
    }

    private static Account assetAccount() {
        Account account = new Account();
        account.setId(UUID.randomUUID());
        account.setAccountType(AccountType.asset);
        account.setNormalBalance("D");
        return account;
    }

    private static Account liabilityAccount() {
        Account account = new Account();
        account.setId(UUID.randomUUID());
        account.setAccountType(AccountType.liability);
        account.setNormalBalance("C");
        return account;
    }
}
