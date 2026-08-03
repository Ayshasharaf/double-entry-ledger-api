package com.doubleledger.ledger;

import com.doubleledger.ledger.model.Account;
import com.doubleledger.ledger.model.AccountStatus;
import com.doubleledger.ledger.model.AccountType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

//tis file tests just one function Account.validateBalance(). to ensure that it wont allow overdraft when not allowed or when limit is exceeded or when account is frozen.
//unit tests

class AccountTest { //no private or public modifiers needed 

    private Account account;

    @BeforeEach //runs before each test
    //junit creates accounttest object for each test and before each test it calls setUp method
    void setUp() {
        //default values
        account = new Account(); //create a new account
        account.setId(UUID.randomUUID()); //random account id
        account.setName("wallet"); //account name
        account.setAccountType(AccountType.asset); //asset account type
        account.setNormalBalance("D"); //debit balance default
        account.setCurrency("USD"); //currency default
        account.setAllowOverdraft(false); //no overdraft allowed default
        account.setOverdraftLimitMinorUnits(0L); //no overdraft limit default
        account.setStatus(AccountStatus.active); //account is active default
    }

    @Test //marks the test method
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
    
    @Test
    void validateBalance_rejectsClosedAccount() {
        account.setStatus(AccountStatus.closed);

        assertThatThrownBy(() -> account.validateBalance(100))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }


}
