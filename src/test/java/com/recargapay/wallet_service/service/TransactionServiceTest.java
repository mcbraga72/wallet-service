package com.recargapay.wallet_service.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.recargapay.wallet_service.domain.IdempotencyKey;
import com.recargapay.wallet_service.domain.Transaction;
import com.recargapay.wallet_service.domain.Wallet;
import com.recargapay.wallet_service.dto.response.ApiResponse;
import com.recargapay.wallet_service.dto.response.TransactionResponse;
import com.recargapay.wallet_service.dto.response.TransferResponse;
import com.recargapay.wallet_service.enums.IdempotencyKeyStatus;
import com.recargapay.wallet_service.exception.ForbiddenException;
import com.recargapay.wallet_service.repository.IdempotencyKeyRepository;
import com.recargapay.wallet_service.repository.TransactionRepository;
import com.recargapay.wallet_service.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Mock
    private IdempotencyKeyService idempotencyKeyService;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void shouldDepositSuccessfully() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(100);

        Wallet wallet = new Wallet(userId);
        wallet.prePersist();

        when(idempotencyKeyRepository.findByIdempotencyKeyAndUserPublicId(key, userId))
                .thenReturn(Optional.empty());

        when(walletRepository.findByPublicIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));

        when(idempotencyKeyService.saveOrReplay(
                eq(key),
                eq(userId),
                any(),
                any()
        )).thenAnswer(invocation -> invocation.getArgument(2));

        // Act
        ApiResponse<TransactionResponse> result = transactionService.deposit(userId, key, walletId, amount);

        // Assert
        assertNotNull(result);
        assertEquals("Deposit successful", result.message());
        assertEquals(amount, result.data().amount());

        verify(transactionRepository).save(any(Transaction.class));
        verify(idempotencyKeyService).saveOrReplay(eq(key), eq(userId), any(), any());
    }

    @Test
    void shouldReturnExistingResponseWhenIdempotencyKeyExistsOnDeposit() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(100);

        ApiResponse<TransactionResponse> expected = ApiResponse.success(
                "Deposit successful",
                new TransactionResponse(UUID.randomUUID(), amount)
        );

        IdempotencyKey storedKey = new IdempotencyKey(
                key,
                userId,
                IdempotencyKeyStatus.SUCCESS,
                "json"
        );

        when(idempotencyKeyRepository.findByIdempotencyKeyAndUserPublicId(key, userId))
                .thenReturn(Optional.of(storedKey));

        when(idempotencyKeyService.deserialize(any(), any()))
                .thenReturn(expected);

        // Act
        ApiResponse<TransactionResponse> result = transactionService.deposit(userId, key, walletId, amount);

        // Assert
        assertEquals(expected, result);

        verify(walletRepository, never()).findByPublicIdForUpdate(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenWalletNotFoundOnDeposit() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        when(idempotencyKeyRepository.findByIdempotencyKeyAndUserPublicId(key, userId))
                .thenReturn(Optional.empty());

        when(walletRepository.findByPublicIdForUpdate(walletId))
                .thenReturn(Optional.empty());

        // Act
        assertThrows(IllegalArgumentException.class, () -> transactionService.deposit(userId, key, walletId, BigDecimal.TEN));

        // Assert
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void shouldThrowForbiddenWhenUserDoesNotOwnWalletOnDeposit() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        Wallet wallet = new Wallet(UUID.randomUUID()); // different owner
        wallet.prePersist();

        when(idempotencyKeyRepository.findByIdempotencyKeyAndUserPublicId(key, userId))
                .thenReturn(Optional.empty());

        when(walletRepository.findByPublicIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));

        // Act
        assertThrows(ForbiddenException.class, () -> transactionService.deposit(userId, key, walletId, BigDecimal.TEN));

        // Assert
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void shouldReplayResponseWhenIdempotencyConflictOccursOnDeposit() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        BigDecimal amount = BigDecimal.TEN;

        ApiResponse<TransactionResponse> expected = ApiResponse.success(
                "Deposit successful",
                new TransactionResponse(UUID.randomUUID(), amount)
        );

        IdempotencyKey existing = new IdempotencyKey(
                key,
                userId,
                IdempotencyKeyStatus.SUCCESS,
                "json"
        );

        when(idempotencyKeyRepository.findByIdempotencyKeyAndUserPublicId(key, userId))
                .thenReturn(Optional.of(existing));

        when(idempotencyKeyService.deserialize(eq("json"), any(TypeReference.class)))
                .thenReturn(expected);

        // Act
        ApiResponse<TransactionResponse> result = transactionService.deposit(userId, key, walletId, amount);

        // Assert
        assertEquals(expected, result);

        verify(idempotencyKeyRepository).findByIdempotencyKeyAndUserPublicId(key, userId);
        verify(idempotencyKeyService).deserialize(eq("json"), any(TypeReference.class));
        verify(walletRepository, never()).findByPublicIdForUpdate(any());
        verify(transactionRepository, never()).save(any());
        verify(idempotencyKeyService, never()).saveOrReplay(any(), any(), any(), any());
    }

    @Test
    void shouldWithdrawSuccessfully() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(100);

        Wallet wallet = new Wallet(userId);
        wallet.prePersist();
        ReflectionTestUtils.setField(wallet, "id", 1L);

        when(idempotencyKeyRepository.findByIdempotencyKeyAndUserPublicId(key, userId))
                .thenReturn(Optional.empty());

        when(walletRepository.findByPublicIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));

        when(transactionRepository.getBalance(anyLong()))
                .thenReturn(BigDecimal.valueOf(200));

        doAnswer(invocation -> invocation.getArgument(2))
                .when(idempotencyKeyService)
                .saveOrReplay(eq(key), eq(userId), any(), any());

        // Act
        ApiResponse<TransactionResponse> result = transactionService.withdraw(userId, key, walletId, amount);

        // Assert
        assertNotNull(result);
        assertEquals("Withdraw successful", result.message());
        assertEquals(amount, result.data().amount());

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());

        Transaction saved = captor.getValue();
        assertEquals(amount.negate(), saved.getAmount());

        verify(idempotencyKeyService).saveOrReplay(eq(key), eq(userId), any(), any());
    }

    @Test
    void shouldThrowWhenInsufficientFundsOnWithdraw() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        Wallet wallet = new Wallet(userId);
        wallet.prePersist();
        ReflectionTestUtils.setField(wallet, "id", 1L);

        when(idempotencyKeyRepository.findByIdempotencyKeyAndUserPublicId(key, userId))
                .thenReturn(Optional.empty());

        when(walletRepository.findByPublicIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));

        when(transactionRepository.getBalance(anyLong()))
                .thenReturn(BigDecimal.valueOf(50));

        // Act
        assertThrows(IllegalArgumentException.class,
                () -> transactionService.withdraw(userId, key, walletId, BigDecimal.valueOf(100)));

        // Assert
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void shouldReturnExistingResponseWhenIdempotencyKeyExistsOnWithdraw() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(100);

        ApiResponse<TransactionResponse> expected = ApiResponse.success(
                "Withdraw successful",
                new TransactionResponse(UUID.randomUUID(), amount)
        );

        IdempotencyKey storedKey = new IdempotencyKey(
                key,
                userId,
                IdempotencyKeyStatus.SUCCESS,
                "json"
        );

        when(idempotencyKeyRepository.findByIdempotencyKeyAndUserPublicId(key, userId))
                .thenReturn(Optional.of(storedKey));

        when(idempotencyKeyService.deserialize(eq("json"), any()))
                .thenReturn(expected);

        // Act
        ApiResponse<TransactionResponse> result = transactionService.withdraw(userId, key, walletId, amount);

        // Assert
        assertEquals(expected.message(), result.message());
        assertEquals(expected.data(), result.data());

        verify(idempotencyKeyRepository).findByIdempotencyKeyAndUserPublicId(key, userId);
        verify(idempotencyKeyService).deserialize(eq("json"), any());
        verify(walletRepository, never()).findByPublicIdForUpdate(any());
        verify(transactionRepository, never()).save(any());
        verifyNoMoreInteractions(walletRepository, transactionRepository);
    }

    @Test
    void shouldThrowWhenWalletNotFoundOnWithdraw() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        when(idempotencyKeyRepository.findByIdempotencyKeyAndUserPublicId(key, userId))
                .thenReturn(Optional.empty());

        when(walletRepository.findByPublicIdForUpdate(walletId))
                .thenReturn(Optional.empty());

        // Act
        assertThrows(IllegalArgumentException.class,
                () -> transactionService.withdraw(userId, key, walletId, BigDecimal.TEN));

        // Assert
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void shouldThrowForbiddenWhenUserDoesNotOwnWalletOnWithdraw() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        Wallet wallet = new Wallet(UUID.randomUUID()); // different owner
        wallet.prePersist();

        when(idempotencyKeyRepository.findByIdempotencyKeyAndUserPublicId(key, userId))
                .thenReturn(Optional.empty());

        when(walletRepository.findByPublicIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));

        // Act
        assertThrows(ForbiddenException.class,
                () -> transactionService.withdraw(userId, key, walletId, BigDecimal.TEN));

        // Assert
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void shouldReplayResponseWhenIdempotencyConflictOccursOnWithdraw() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        BigDecimal amount = BigDecimal.TEN;

        ApiResponse<TransactionResponse> expected = ApiResponse.success(
                "Withdraw successful",
                new TransactionResponse(UUID.randomUUID(), amount)
        );

        IdempotencyKey existing = new IdempotencyKey(
                key,
                userId,
                IdempotencyKeyStatus.SUCCESS,
                "json"
        );

        when(idempotencyKeyRepository.findByIdempotencyKeyAndUserPublicId(key, userId))
                .thenReturn(Optional.of(existing));

        when(idempotencyKeyService.deserialize(eq("json"), any(TypeReference.class)))
                .thenReturn(expected);

        // Act
        ApiResponse<TransactionResponse> result = transactionService.withdraw(userId, key, walletId, amount);

        // Assert
        assertEquals(expected, result);
    }

    @Test
    void shouldTransferSuccessfully() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID fromWalletId = UUID.randomUUID();
        UUID toWalletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(100);

        Wallet from = new Wallet(userId);
        from.prePersist();
        ReflectionTestUtils.setField(from, "id", 1L);
        ReflectionTestUtils.setField(from, "publicId", fromWalletId);

        Wallet to = new Wallet(UUID.randomUUID());
        to.prePersist();
        ReflectionTestUtils.setField(to, "id", 2L);
        ReflectionTestUtils.setField(to, "publicId", toWalletId);

        when(idempotencyKeyRepository.findByIdempotencyKeyAndUserPublicId(key, userId))
                .thenReturn(Optional.empty());

        when(walletRepository.findByPublicIdForUpdate(eq(fromWalletId)))
                .thenReturn(Optional.of(from));

        when(walletRepository.findByPublicIdForUpdate(eq(toWalletId)))
                .thenReturn(Optional.of(to));

        when(transactionRepository.getBalance(1L))
                .thenReturn(BigDecimal.valueOf(200));

        doAnswer(inv -> inv.getArgument(2))
                .when(idempotencyKeyService)
                .saveOrReplay(eq(key), eq(userId), any(), any());

        // Act
        ApiResponse<TransferResponse> result = transactionService.transfer(userId, key, fromWalletId, toWalletId, amount);

        // Assert
        assertEquals("Transfer successful", result.message());
        assertEquals(amount, result.data().amount());
        assertEquals(fromWalletId, result.data().fromWalletId());
        assertEquals(toWalletId, result.data().toWalletId());

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(captor.capture());

        List<Transaction> saved = captor.getAllValues();

        assertTrue(saved.stream().anyMatch(t -> t.getAmount().compareTo(amount.negate()) == 0));
        assertTrue(saved.stream().anyMatch(t -> t.getAmount().compareTo(amount) == 0));
    }

    @Test
    void shouldReturnExistingResponseWhenIdempotencyKeyExistsForTransfer() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID fromWalletId = UUID.randomUUID();
        UUID toWalletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        ApiResponse<TransferResponse> expected = ApiResponse.success(
                "Transfer successful",
                new TransferResponse(UUID.randomUUID(), fromWalletId, toWalletId, BigDecimal.TEN)
        );

        IdempotencyKey stored = new IdempotencyKey(key, userId, IdempotencyKeyStatus.SUCCESS, "json");

        when(idempotencyKeyRepository.findByIdempotencyKeyAndUserPublicId(key, userId))
                .thenReturn(Optional.of(stored));

        when(idempotencyKeyService.deserialize(eq("json"), any()))
                .thenReturn(expected);

        // Act
        ApiResponse<TransferResponse> result = transactionService.transfer(userId, key, fromWalletId, toWalletId, BigDecimal.TEN);

        // Assert
        assertEquals(expected.message(), result.message());

        verify(walletRepository, never()).findByPublicIdForUpdate(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenSameWallet() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        // Assert
        assertThrows(IllegalArgumentException.class,
                () -> transactionService.transfer(userId, key, walletId, walletId, BigDecimal.TEN));
    }

    @Test
    void shouldThrowWhenWalletNotFound() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID fromWalletId = UUID.randomUUID();
        UUID toWalletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        when(idempotencyKeyRepository.findByIdempotencyKeyAndUserPublicId(any(), any()))
                .thenReturn(Optional.empty());

        when(walletRepository.findByPublicIdForUpdate(any()))
                .thenReturn(Optional.empty());

        // Assert
        assertThrows(IllegalArgumentException.class,
                () -> transactionService.transfer(userId, key, fromWalletId, toWalletId, BigDecimal.TEN));
    }

    @Test
    void shouldThrowForbiddenWhenUserDoesNotOwnSourceWalletOnTransfer() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID fromWalletId = UUID.randomUUID();
        UUID toWalletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        Wallet from = new Wallet(UUID.randomUUID()); // different owner
        from.prePersist();
        ReflectionTestUtils.setField(from, "id", 1L);
        ReflectionTestUtils.setField(from, "publicId", fromWalletId);

        Wallet to = new Wallet(UUID.randomUUID());
        to.prePersist();
        ReflectionTestUtils.setField(to, "id", 2L);
        ReflectionTestUtils.setField(to, "publicId", toWalletId);

        when(idempotencyKeyRepository.findByIdempotencyKeyAndUserPublicId(any(), any()))
                .thenReturn(Optional.empty());

        when(walletRepository.findByPublicIdForUpdate(eq(fromWalletId)))
                .thenReturn(Optional.of(from));

        when(walletRepository.findByPublicIdForUpdate(eq(toWalletId)))
                .thenReturn(Optional.of(to));

        // Assert
        assertThrows(ForbiddenException.class,
                () -> transactionService.transfer(userId, key, fromWalletId, toWalletId, BigDecimal.TEN));
    }

    @Test
    void shouldThrowWhenInsufficientFundsOnTransfer() {
        UUID userId = UUID.randomUUID();
        UUID fromWalletId = UUID.randomUUID();
        UUID toWalletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        Wallet from = new Wallet(userId);
        from.prePersist();
        ReflectionTestUtils.setField(from, "id", 1L);
        ReflectionTestUtils.setField(from, "publicId", fromWalletId);

        Wallet to = new Wallet(UUID.randomUUID());
        to.prePersist();
        ReflectionTestUtils.setField(to, "id", 2L);
        ReflectionTestUtils.setField(to, "publicId", toWalletId);

        when(idempotencyKeyRepository.findByIdempotencyKeyAndUserPublicId(any(), any()))
                .thenReturn(Optional.empty());

        when(walletRepository.findByPublicIdForUpdate(eq(fromWalletId)))
                .thenReturn(Optional.of(from));

        when(walletRepository.findByPublicIdForUpdate(eq(toWalletId)))
                .thenReturn(Optional.of(to));

        when(transactionRepository.getBalance(1L))
                .thenReturn(BigDecimal.valueOf(50));

        assertThrows(IllegalArgumentException.class,
                () -> transactionService.transfer(userId, key, fromWalletId, toWalletId, BigDecimal.valueOf(100)));

        verify(transactionRepository, never()).save(any());
    }
}
