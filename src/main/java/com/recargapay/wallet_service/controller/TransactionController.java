package com.recargapay.wallet_service.controller;

import com.recargapay.wallet_service.dto.request.AmountRequest;
import com.recargapay.wallet_service.dto.request.TransferRequest;
import com.recargapay.wallet_service.dto.response.ApiResponse;
import com.recargapay.wallet_service.dto.response.TransactionResponse;
import com.recargapay.wallet_service.dto.response.TransferResponse;
import com.recargapay.wallet_service.service.TransactionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/wallets/{walletId}/deposit")
    public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @PathVariable UUID walletId,
            @Valid @RequestBody AmountRequest request
    ) {
        ApiResponse<TransactionResponse> response =
                transactionService.deposit(userId, idempotencyKey, walletId, request.amount());

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/wallets/{walletId}/withdraw")
    public ResponseEntity<ApiResponse<TransactionResponse>> withdraw(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @PathVariable UUID walletId,
            @Valid @RequestBody AmountRequest request
    ) {
        ApiResponse<TransactionResponse> response =
                transactionService.withdraw(userId, idempotencyKey, walletId, request.amount());

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/transfers")
    public ResponseEntity<ApiResponse<TransferResponse>> transfer(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody TransferRequest request
    ) {
        ApiResponse<TransferResponse> response =
                transactionService.transfer(
                        userId,
                        idempotencyKey,
                        request.fromWalletId(),
                        request.toWalletId(),
                        request.amount()
                );

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
