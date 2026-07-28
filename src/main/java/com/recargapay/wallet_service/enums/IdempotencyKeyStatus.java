package com.recargapay.wallet_service.enums;

public enum IdempotencyKeyStatus {
    PROCESSING,
    SUCCESS,
    FAILED;

    public boolean isProcessing() {
        return this == PROCESSING;
    }

    public boolean isSuccessful() {
        return this == SUCCESS;
    }

    public boolean isFailed() {
        return this == FAILED;
    }
}