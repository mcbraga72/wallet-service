package com.recargapay.wallet_service.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateWalletRequest(
        @NotNull UUID userId
) {
}
