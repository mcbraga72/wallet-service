package com.recargapay.wallet_service.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferResponse(
        UUID referenceId,
        UUID fromWalletId,
        UUID toWalletId,
        BigDecimal amount
) {
}
