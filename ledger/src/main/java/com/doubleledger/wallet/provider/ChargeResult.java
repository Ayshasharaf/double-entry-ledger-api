package com.doubleledger.wallet.provider;

public record ChargeResult(boolean succeeded, String providerRef, String failureReason) {

    public static ChargeResult success(String providerRef) {
        return new ChargeResult(true, providerRef, null);
    }

    public static ChargeResult failure(String failureReason) {
        return new ChargeResult(false, null, failureReason);
    }
}
