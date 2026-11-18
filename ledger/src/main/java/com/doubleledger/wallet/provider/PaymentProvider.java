package com.doubleledger.wallet.provider;

/**
 * Card/network charge. Implementations must not open a database transaction.
 * Repeated calls with the same idempotency key must return the original result
 * without charging again. TopupService calls this only after a PROCESSING row is committed.
 */
public interface PaymentProvider {

    ChargeResult charge(long amountMinorUnits, String currency, String idempotencyKey);
}
