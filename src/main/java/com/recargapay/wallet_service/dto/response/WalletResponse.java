package com.recargapay.wallet_service.dto.response;

import java.time.Instant;
import java.util.UUID;

public record WalletResponse(
        UUID publicId,
        UUID userId,
        Instant createdAt
) {
}
