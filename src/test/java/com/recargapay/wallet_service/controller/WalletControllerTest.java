package com.recargapay.wallet_service.controller;

import com.recargapay.wallet_service.domain.Wallet;
import com.recargapay.wallet_service.exception.ForbiddenException;
import com.recargapay.wallet_service.exception.NotFoundException;
import com.recargapay.wallet_service.fixture.WalletFixture;
import com.recargapay.wallet_service.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WalletController.class)
public class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WalletService walletService;

    @Test
    void shouldCreateWallet() throws Exception {
        // Arrange
        UUID userId = UUID.randomUUID();
        Wallet wallet = WalletFixture.wallet(userId);
        String generatedPublicId = wallet.getPublicId().toString();

        when(walletService.createWallet(userId)).thenReturn(wallet);

        // Act and Assert
        mockMvc.perform(post("/wallets")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Wallet created successfully"))
                .andExpect(jsonPath("$.data.publicId").value(generatedPublicId))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.createdAt").exists());

        // Assert
        verify(walletService).createWallet(userId);
    }

    @Test
    void shouldNotCreateWalletWhenItAlreadyExists() throws Exception {
        // Arrange
        UUID userId = UUID.randomUUID();

        when(walletService.createWallet(userId))
                .thenThrow(new IllegalArgumentException("Wallet already exists"));

        // Act and Assert
        mockMvc.perform(post("/wallets")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Wallet already exists"));

        // Assert
        verify(walletService).createWallet(userId);
    }

    @Test
    void shouldReturnBadRequestWhenUserIdHeaderMissing() throws Exception {
        // Act and Assert
        mockMvc.perform(post("/wallets"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenUserIdIsInvalidUUID() throws Exception {
        // Act and Assert
        mockMvc.perform(post("/wallets")
                        .header("X-User-Id", "invalid-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldGetBalance() throws Exception {
        // Arrange
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BigDecimal balance = BigDecimal.valueOf(100);

        when(walletService.getBalance(userId, walletId)).thenReturn(balance);

        // Act and Assert
        mockMvc.perform(get("/wallets/{id}/balance", walletId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Balance retrieved successfully"))
                .andExpect(jsonPath("$.data.balance").value(100));

        // Assert
        verify(walletService).getBalance(userId, walletId);
        verify(walletService, never()).getBalanceAt(any(), any(), any());
    }

    @Test
    void shouldGetBalanceAtSpecificTime() throws Exception {
        // Arrange
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant at = Instant.now();
        BigDecimal balance = BigDecimal.valueOf(50);

        when(walletService.getBalanceAt(userId, walletId, at)).thenReturn(balance);

        // Act and Assert
        mockMvc.perform(get("/wallets/{id}/balance", walletId)
                        .header("X-User-Id", userId.toString())
                        .param("at", at.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(50));

        // Assert
        verify(walletService).getBalanceAt(userId, walletId, at);
        verify(walletService, never()).getBalance(any(), any());
    }

    @Test
    void shouldReturnZeroBalance() throws Exception {
        // Arrange
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(walletService.getBalance(userId, walletId))
                .thenReturn(BigDecimal.ZERO);

        // Act and Assert
        mockMvc.perform(get("/wallets/{id}/balance", walletId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(0));
    }

    @Test
    void shouldReturnNotFoundWhenWalletDoesNotExist() throws Exception {
        // Arrange
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(walletService.getBalance(userId, walletId))
                .thenThrow(new NotFoundException("Wallet not found"));

        // Act and Assert
        mockMvc.perform(get("/wallets/{id}/balance", walletId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnForbiddenWhenUserDoesNotOwnWallet() throws Exception {
        // Arrange
        UUID walletId = UUID.randomUUID();
        UUID unauthorizedUserId = UUID.randomUUID();

        when(walletService.getBalance(unauthorizedUserId, walletId))
                .thenThrow(new ForbiddenException("Access denied"));

        // Act and Assert
        mockMvc.perform(get("/wallets/{id}/balance", walletId)
                        .header("X-User-Id", unauthorizedUserId.toString()))
                .andExpect(status().isForbidden());
    }
}
