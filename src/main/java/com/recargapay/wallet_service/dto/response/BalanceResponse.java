package com.recargapay.wallet_service.dto.response;

import java.math.BigDecimal;

public record BalanceResponse(
        BigDecimal balance
) {
}
