package com.recargapay.wallet_service.dto.response;

import com.recargapay.wallet_service.exception.ErrorResponse;

import java.time.Instant;

public record ApiResponse<T>(
        Instant timestamp,
        int status,
        String message,
        T data,
        ErrorResponse error
) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(Instant.now(), 200, message, data, null);
    }

    public static <T> ApiResponse<T> error(int status, String message, ErrorResponse error) {
        return new ApiResponse<>(Instant.now(), status, message, null, error);
    }
}