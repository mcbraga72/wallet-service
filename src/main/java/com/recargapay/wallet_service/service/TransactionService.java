package com.recargapay.wallet_service.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.recargapay.wallet_service.domain.IdempotencyKey;
import com.recargapay.wallet_service.domain.Transaction;
import com.recargapay.wallet_service.domain.Wallet;
import com.recargapay.wallet_service.dto.response.ApiResponse;
import com.recargapay.wallet_service.dto.response.TransactionResponse;
import com.recargapay.wallet_service.dto.response.TransferResponse;
import com.recargapay.wallet_service.enums.TransactionStatus;
import com.recargapay.wallet_service.enums.TransactionType;
import com.recargapay.wallet_service.exception.ForbiddenException;
import com.recargapay.wallet_service.repository.IdempotencyKeyRepository;
import com.recargapay.wallet_service.repository.TransactionRepository;
import com.recargapay.wallet_service.repository.WalletRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Service
public class TransactionService {

    private final IdempotencyKeyService idempotencyKeyService;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;

    public TransactionService(
            IdempotencyKeyService idempotencyKeyService,
            WalletRepository walletRepository,
            TransactionRepository transactionRepository,
            IdempotencyKeyRepository idempotencyKeyRepository
    ) {
        this.idempotencyKeyService = idempotencyKeyService;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
    }

    @Transactional
    public ApiResponse<TransactionResponse> deposit(
            UUID userId,
            UUID idempotencyKey,
            UUID walletId,
            BigDecimal amount
    ) {
        Optional<IdempotencyKey> key = idempotencyKeyRepository.findByIdempotencyKeyAndUserPublicId(idempotencyKey, userId);

        if (key.isPresent()) {
            return idempotencyKeyService.deserialize(
                    key.get().getResponse(),
                    new TypeReference<ApiResponse<TransactionResponse>>() {}
            );
        }

        Wallet wallet = walletRepository.findByPublicIdForUpdate(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));

        if (!wallet.getUserId().equals(userId)) {
            log.warn("User {} attempted to access wallet {} owned by user {}", userId, walletId, wallet.getUserId());
            throw new ForbiddenException("Access denied");
        }

        Transaction transaction = new Transaction(
                wallet,
                amount,
                TransactionType.DEPOSIT,
                TransactionStatus.SUCCESS,
                null
        );

        transactionRepository.save(transaction);

        TransactionResponse response = new TransactionResponse(
                transaction.getPublicId(),
                amount
        );

        ApiResponse<TransactionResponse> apiResponse = ApiResponse.success("Deposit successful", response);

        return idempotencyKeyService.saveOrReplay(
                idempotencyKey,
                userId,
                apiResponse,
                new TypeReference<ApiResponse<TransactionResponse>>() {}
        );
    }

    @Transactional
    public ApiResponse<TransactionResponse> withdraw(
            UUID userId,
            UUID idempotencyKey,
            UUID walletId,
            BigDecimal amount
    ) {
        Optional<IdempotencyKey> key = idempotencyKeyRepository.findByIdempotencyKeyAndUserPublicId(idempotencyKey, userId);

        if (key.isPresent()) {
            return idempotencyKeyService.deserialize(
                    key.get().getResponse(),
                    new TypeReference<ApiResponse<TransactionResponse>>() {}
            );
        }

        Wallet wallet = walletRepository.findByPublicIdForUpdate(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));

        if (!wallet.getUserId().equals(userId)) {
            log.warn("User {} attempted to access wallet {} owned by user {}", userId, walletId, wallet.getUserId());
            throw new ForbiddenException("Access denied");
        }

        BigDecimal balance = transactionRepository.getBalance(wallet.getId());

        if (balance.compareTo(amount) < 0) {
            log.warn("Insufficient funds: walletId={}, balance={}, requested={}", walletId, balance, amount);
            throw new IllegalArgumentException("Insufficient funds");
        }

        Transaction transaction = new Transaction(
                wallet,
                amount.negate(),
                TransactionType.WITHDRAW,
                TransactionStatus.SUCCESS,
                null
        );

        transactionRepository.save(transaction);

        TransactionResponse response = new TransactionResponse(transaction.getPublicId(), amount);

        ApiResponse<TransactionResponse> apiResponse = ApiResponse.success("Withdraw successful", response);

        return idempotencyKeyService.saveOrReplay(
                idempotencyKey,
                userId,
                apiResponse,
                new TypeReference<ApiResponse<TransactionResponse>>() {}
        );
    }

    @Transactional
    public ApiResponse<TransferResponse> transfer(
            UUID userId,
            UUID idempotencyKey,
            UUID fromWalletId,
            UUID toWalletId,
            BigDecimal amount
    ) {
        if (fromWalletId.equals(toWalletId)) {
            throw new IllegalArgumentException("Cannot transfer to same wallet");
        }

        Optional<IdempotencyKey> key = idempotencyKeyRepository.findByIdempotencyKeyAndUserPublicId(idempotencyKey, userId);

        if (key.isPresent()) {
            return idempotencyKeyService.deserialize(
                    key.get().getResponse(),
                    new TypeReference<ApiResponse<TransferResponse>>() {}
            );
        }

        // Lock wallets in consistent order (avoid deadlocks)
        List<UUID> ordered = Stream.of(fromWalletId, toWalletId)
                .sorted()
                .toList();

        Wallet first = walletRepository.findByPublicIdForUpdate(ordered.get(0))
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));

        Wallet second = walletRepository.findByPublicIdForUpdate(ordered.get(1))
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));

        Wallet from = first.getPublicId().equals(fromWalletId) ? first : second;
        Wallet to = first.getPublicId().equals(toWalletId) ? first : second;

        if (!from.getUserId().equals(userId)) {
            log.warn("User {} attempted to access wallet {} owned by user {}", userId, fromWalletId, from.getUserId());
            throw new ForbiddenException("Access denied");
        }

        BigDecimal balance = transactionRepository.getBalance(from.getId());

        if (balance.compareTo(amount) < 0) {
            log.warn("Insufficient funds: walletId={}, balance={}, requested={}", fromWalletId, balance, amount);
            throw new IllegalArgumentException("Insufficient funds");
        }

        UUID referenceId = UUID.randomUUID();

        Transaction debit = new Transaction(
                from,
                amount.negate(),
                TransactionType.TRANSFER,
                TransactionStatus.SUCCESS,
                referenceId
        );

        Transaction credit = new Transaction(
                to,
                amount,
                TransactionType.TRANSFER,
                TransactionStatus.SUCCESS,
                referenceId
        );

        transactionRepository.save(debit);
        transactionRepository.save(credit);

        TransferResponse response = new TransferResponse(
                referenceId,
                from.getPublicId(),
                to.getPublicId(),
                amount
        );

        ApiResponse<TransferResponse> apiResponse = ApiResponse.success("Transfer successful", response);

        return idempotencyKeyService.saveOrReplay(
                idempotencyKey,
                userId,
                apiResponse,
                new TypeReference<ApiResponse<TransferResponse>>() {}
        );
    }
}
