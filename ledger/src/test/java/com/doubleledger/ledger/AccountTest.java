package com.doubleledger.ledger;

import com.doubleledger.ledger.model.Account;
import com.doubleledger.ledger.model.AccountStatus;
import com.doubleledger.ledger.model.AccountType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private Account account;

    @BeforeEach
    void setUp() {
        account = new Account();
        account.setId(UUID.randomUUID());
        account.setName("wallet");
        account.setAccountType(AccountType.asset);
        account.setNormalBalance("D");
        account.setCurrency("USD");
        account.setAllowOverdraft(false);
        account.setOverdraftLimitMinorUnits(0L);
        account.setStatus(AccountStatus.active);
    }

    @Test
    void validateBalance_allowsZeroOrPositiveBalance() {
        assertThatCode(() -> account.validateBalance(0)).doesNotThrowAnyException();
        assertThatCode(() -> account.validateBalance(10_000)).doesNotThrowAnyException();
    }

    @Test
    void validateBalance_rejectsOverdraftWhenNotAllowed() {
        assertThatThrownBy(() -> account.validateBalance(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Overdraft not allowed");
    }

    @Test
    void validateBalance_allowsOverdraftWithinLimit() {
        account.setAllowOverdraft(true);
        account.setOverdraftLimitMinorUnits(500L);

        assertThatCode(() -> account.validateBalance(-500)).doesNotThrowAnyException();
    }

    @Test
    void validateBalance_rejectsOverdraftBeyondLimit() {
        account.setAllowOverdraft(true);
        account.setOverdraftLimitMinorUnits(500L);

        assertThatThrownBy(() -> account.validateBalance(-501))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Overdraft limit exceeded");
    }

    @Test
    void validateBalance_rejectsFrozenAccount() {
        account.setStatus(AccountStatus.frozen);

        assertThatThrownBy(() -> account.validateBalance(100))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("frozen");
    }
}
