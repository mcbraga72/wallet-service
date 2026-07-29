package com.recargapay.wallet_service.service;

import com.recargapay.wallet_service.domain.Wallet;
import com.recargapay.wallet_service.exception.ForbiddenException;
import com.recargapay.wallet_service.exception.NotFoundException;
import com.recargapay.wallet_service.fixture.WalletFixture;
import com.recargapay.wallet_service.repository.TransactionRepository;
import com.recargapay.wallet_service.repository.WalletRepository;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private WalletService walletService;

    @Test
    void shouldCreateWalletSuccessfully() {
        // Arrange
        UUID userId = UUID.randomUUID();
        Wallet walletToSave = WalletFixture.wallet(userId);

        when(walletRepository.findByUserId(userId))
                .thenReturn(Optional.empty());

        when(walletRepository.save(any(Wallet.class)))
                .thenReturn(walletToSave);

        // Act
        Wallet createdWallet = walletService.createWallet(userId);

        // Assert
        assertNotNull(createdWallet);
        assertEquals(userId, createdWallet.getUserId());

        verify(walletRepository).save(any(Wallet.class));
    }

    @Test
    void shouldThrowWhenWalletAlreadyExists() {
        // Arrange
        UUID userId = UUID.randomUUID();
        Wallet walletToSave = WalletFixture.wallet(userId);

        when(walletRepository.findByUserId(userId))
                .thenReturn(Optional.of(walletToSave));

        // Act and Assert
        assertThrows(IllegalArgumentException.class,
                () -> walletService.createWallet(userId));
    }

    @Test
    void shouldReturnCurrentBalanceSuccessfully() {
        // Arrange
        Long internalId = 1L;
        UUID userId = UUID.randomUUID();
        UUID walletPublicId = UUID.randomUUID();
        BigDecimal expectedBalance = new BigDecimal("150.00");
        Wallet wallet = WalletFixture.wallet(userId, internalId);

        when(walletRepository.findByPublicId(walletPublicId))
                .thenReturn(Optional.of(wallet));

        when(transactionRepository.getBalance(internalId))
                .thenReturn(expectedBalance);

        // Act
        BigDecimal actualBalance = walletService.getBalance(userId, walletPublicId);

        // Assert
        assertEquals(expectedBalance, actualBalance);
        verify(transactionRepository).getBalance(internalId);
    }

    @Test
    void shouldReturnBalanceAtSpecificTimeSuccessfully() {
        // Arrange
        Long internalId = 1L;
        UUID userId = UUID.randomUUID();
        UUID walletPublicId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        BigDecimal expectedBalance = new BigDecimal("75.50");
        Wallet wallet = WalletFixture.wallet(userId, internalId);

        when(walletRepository.findByPublicId(walletPublicId))
                .thenReturn(Optional.of(wallet));

        when(transactionRepository.getBalanceAt(eq(internalId), any(Instant.class)))
                .thenReturn(expectedBalance);

        // Act
        BigDecimal actualBalance = walletService.getBalanceAt(userId, walletPublicId, timestamp);

        // Assert
        assertEquals(expectedBalance, actualBalance);
        verify(transactionRepository).getBalanceAt(internalId, timestamp);
    }

    @Test
    void shouldThrowForbiddenWhenUserDoesNotOwnWallet() {
        // Arrange
        UUID ownerId = UUID.randomUUID();
        UUID intruderId = UUID.randomUUID();
        UUID walletPublicId = UUID.randomUUID();
        Wallet wallet = WalletFixture.wallet(ownerId);

        when(walletRepository.findByPublicId(walletPublicId))
                .thenReturn(Optional.of(wallet));

        // Act
        Executable action = () -> walletService.getBalance(intruderId, walletPublicId);

        // Assert
        assertThrows(ForbiddenException.class, action);
        verify(transactionRepository, never()).getBalance(any());
    }

    @Test
    void shouldThrowForbiddenWhenUserDoesNotOwnWalletForHistoricalBalance() {
        // Arrange
        UUID ownerId = UUID.randomUUID();
        UUID intruderId = UUID.randomUUID();
        UUID walletPublicId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        Wallet wallet = WalletFixture.wallet(ownerId);

        when(walletRepository.findByPublicId(walletPublicId))
                .thenReturn(Optional.of(wallet));

        // Act
        Executable action = () -> walletService.getBalanceAt(intruderId, walletPublicId, timestamp);

        // Assert
        assertThrows(ForbiddenException.class, action);
        verify(transactionRepository, never()).getBalanceAt(any(), any());
    }

    @Test
    void shouldThrowExceptionWhenWalletNotFound() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID walletPublicId = UUID.randomUUID();

        when(walletRepository.findByPublicId(walletPublicId))
                .thenReturn(Optional.empty());

        // Act
        Executable action = () -> walletService.getBalance(userId, walletPublicId);

        // Assert
        assertThrows(NotFoundException.class, action);
    }
}
