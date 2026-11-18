package com.doubleledger.wallet.provider;

import org.springframework.stereotype.Component;

/**
 * In-process stand-in for Stripe. No HTTP, no DB.
 * Amount {@link #FAIL_AMOUNT_MINOR_UNITS} always fails so tests can hit FAILED + no journal.
 */
@Component
public class FakePaymentProvider implements PaymentProvider {

    /** $9,999.99 — use this amount in tests to force a declined charge. */
    public static final long FAIL_AMOUNT_MINOR_UNITS = 999_999L;

    @Override
    public ChargeResult charge(long amountMinorUnits, String currency, String idempotencyKey) {
        if (amountMinorUnits <= 0) {
            return ChargeResult.failure("amount must be positive");
        }
        if (!"USD".equals(currency)) {
            return ChargeResult.failure("only USD is supported");
        }
        if (amountMinorUnits == FAIL_AMOUNT_MINOR_UNITS) {
            return ChargeResult.failure("card declined");
        }
        return ChargeResult.success("fake_" + idempotencyKey);
    }
}
