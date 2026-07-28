package com.recargapay.wallet_service.exception;

public record ErrorResponse(
        ErrorCode code,
        String details
) {
}
