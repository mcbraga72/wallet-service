package com.recargapay.wallet_service.enums;

public enum TransactionType {
    DEPOSIT,
    WITHDRAW,
    TRANSFER;

    public boolean isCredit() {
        return this == DEPOSIT;
    }

    public boolean isDebit() {
        return this == WITHDRAW || this == TRANSFER;
    }
}