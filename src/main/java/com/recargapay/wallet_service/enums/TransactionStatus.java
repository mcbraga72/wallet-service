package com.recargapay.wallet_service.enums;

public enum TransactionStatus {
    PROCESSING,
    SUCCESS,
    FAILED;

    public boolean isFinal() {
        return this == SUCCESS || this == FAILED;
    }

    public boolean isSuccessful() {
        return this == SUCCESS;
    }

    public boolean isFailed() {
        return this == FAILED;
    }
}