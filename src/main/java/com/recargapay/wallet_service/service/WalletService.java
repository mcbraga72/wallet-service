package com.recargapay.wallet_service.service;

import com.recargapay.wallet_service.domain.Wallet;
import com.recargapay.wallet_service.exception.ForbiddenException;
import com.recargapay.wallet_service.exception.NotFoundException;
import com.recargapay.wallet_service.repository.TransactionRepository;
import com.recargapay.wallet_service.repository.WalletRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    public WalletService(WalletRepository walletRepository, TransactionRepository transactionRepository) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    public Wallet createWallet(UUID userId) {
        walletRepository.findByUserId(userId)
                .ifPresent(w -> { throw new IllegalArgumentException("Wallet already exists"); });

        Wallet wallet = new Wallet(userId);
        return walletRepository.save(wallet);
    }

    public BigDecimal getBalance(UUID userId, UUID walletPublicId) {
        Wallet wallet = walletRepository.findByPublicId(walletPublicId)
                .orElseThrow(() -> new NotFoundException("Wallet not found"));

        if (!wallet.getUserId().equals(userId)) {
            throw new ForbiddenException("Access denied");
        }

        return transactionRepository.getBalance(wallet.getId());
    }

    public BigDecimal getBalanceAt(UUID userId, UUID walletPublicId, Instant timestamp) {
        Wallet wallet = walletRepository.findByPublicId(walletPublicId)
                .orElseThrow(() -> new NotFoundException("Wallet not found"));

        if (!wallet.getUserId().equals(userId)) {
            throw new ForbiddenException("Access denied");
        }

        return transactionRepository.getBalanceAt(wallet.getId(), timestamp);
    }
}
