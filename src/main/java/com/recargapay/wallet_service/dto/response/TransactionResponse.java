package com.recargapay.wallet_service.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionResponse(
        UUID transactionId,
        BigDecimal amount
) {
}
