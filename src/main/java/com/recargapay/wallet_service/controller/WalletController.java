package com.recargapay.wallet_service.controller;

import com.recargapay.wallet_service.domain.Wallet;
import com.recargapay.wallet_service.dto.response.ApiResponse;
import com.recargapay.wallet_service.dto.response.BalanceResponse;
import com.recargapay.wallet_service.dto.response.WalletResponse;
import com.recargapay.wallet_service.service.WalletService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WalletResponse>> createWallet(
            @RequestHeader("X-User-Id") UUID userId
    ) {
        Wallet wallet = walletService.createWallet(userId);

        WalletResponse response = new WalletResponse(
                wallet.getPublicId(),
                wallet.getUserId(),
                wallet.getCreatedAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Wallet created successfully", response));
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<ApiResponse<BalanceResponse>> getBalance(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id,
            @RequestParam(required = false) Instant at
    ) {
        BigDecimal balance = (at == null) ? walletService.getBalance(userId, id) : walletService.getBalanceAt(userId, id, at);

        BalanceResponse response = new BalanceResponse(balance);

        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success("Balance retrieved successfully", response));
    }
}
