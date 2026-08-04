package com.recargapay.wallet_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recargapay.wallet_service.dto.request.AmountRequest;
import com.recargapay.wallet_service.dto.request.TransferRequest;
import com.recargapay.wallet_service.dto.response.ApiResponse;
import com.recargapay.wallet_service.dto.response.TransactionResponse;
import com.recargapay.wallet_service.dto.response.TransferResponse;
import com.recargapay.wallet_service.exception.ForbiddenException;
import com.recargapay.wallet_service.exception.GlobalExceptionHandler;
import com.recargapay.wallet_service.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.when;

@Import({TransactionControllerTest.Config.class, GlobalExceptionHandler.class})
@WebMvcTest(TransactionController.class)
public class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    @Autowired
    private ObjectMapper objectMapper;

    @TestConfiguration
    static class Config {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }

    @Test
    void shouldDepositSuccessfully() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        AmountRequest request = new AmountRequest(BigDecimal.valueOf(50));

        TransactionResponse transactionResponse = new TransactionResponse(UUID.randomUUID(), request.amount());

        ApiResponse<TransactionResponse> apiResponse =
                ApiResponse.success("Deposit successful", transactionResponse);

        when(transactionService.deposit(userId, key, walletId, request.amount()))
                .thenReturn(apiResponse);

        mockMvc.perform(post("/wallets/{walletId}/deposit", walletId)
                        .header("X-User-Id", userId.toString())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Deposit successful"))
                .andExpect(jsonPath("$.data.transactionId").value(transactionResponse.transactionId().toString()))
                .andExpect(jsonPath("$.data.amount").value(50));

        verify(transactionService).deposit(userId, key, walletId, request.amount());
    }

    @Test
    void shouldReturnBadRequestWhenUserIdHeaderMissingOnDeposit() throws Exception {
        UUID walletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        AmountRequest request = new AmountRequest(BigDecimal.TEN);

        mockMvc.perform(post("/wallets/{walletId}/deposit", walletId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void shouldReturnBadRequestWhenIdempotencyKeyMissingOnDeposit() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();

        AmountRequest request = new AmountRequest(BigDecimal.TEN);

        mockMvc.perform(post("/wallets/{walletId}/deposit", walletId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void shouldReturnBadRequestWhenIdempotencyKeyBlankOnDeposit() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();

        AmountRequest request = new AmountRequest(BigDecimal.TEN);

        mockMvc.perform(post("/wallets/{walletId}/deposit", walletId)
                        .header("X-User-Id", userId.toString())
                        .header("Idempotency-Key", " ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void shouldReturnBadRequestWhenAmountIsNegativeOnDeposit() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        AmountRequest request = new AmountRequest(BigDecimal.valueOf(-10));

        mockMvc.perform(post("/wallets/{walletId}/deposit", walletId)
                        .header("X-User-Id", userId.toString())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void shouldReturnBadRequestWhenMalformedJsonOnDeposit() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        String invalidJson = "{ invalid json }";

        mockMvc.perform(post("/wallets/{walletId}/deposit", walletId)
                        .header("X-User-Id", userId.toString())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    // Check if it makes sense
    @Test
    void shouldReturnForbiddenWhenServiceThrowsOnDeposit() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        AmountRequest request = new AmountRequest(BigDecimal.TEN);

        when(transactionService.deposit(userId, key, walletId, request.amount()))
                .thenThrow(new ForbiddenException("Access denied"));

        mockMvc.perform(post("/wallets/{walletId}/deposit", walletId)
                        .header("X-User-Id", userId.toString())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldWithdrawSuccessfully() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        AmountRequest request = new AmountRequest(BigDecimal.valueOf(100));

        TransactionResponse txResponse = new TransactionResponse(transactionId, request.amount());

        ApiResponse<TransactionResponse> apiResponse =
                ApiResponse.success("Withdraw successful", txResponse);

        when(transactionService.withdraw(userId, key, walletId, request.amount()))
                .thenReturn(apiResponse);

        mockMvc.perform(post("/wallets/{walletId}/withdraw", walletId)
                        .header("X-User-Id", userId.toString())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Withdraw successful"))
                .andExpect(jsonPath("$.data.transactionId").value(txResponse.transactionId().toString()))
                .andExpect(jsonPath("$.data.amount").value(100));

        verify(transactionService)
                .withdraw(userId, key, walletId, request.amount());
    }

    @Test
    void shouldReturnBadRequestWhenUserIdHeaderMissingOnWithdraw() throws Exception {
        UUID walletId = UUID.randomUUID();

        AmountRequest request = new AmountRequest(BigDecimal.TEN);

        mockMvc.perform(post("/wallets/{walletId}/withdraw", walletId)
                        .header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void shouldReturnBadRequestWhenIdempotencyKeyMissingOnWithdraw() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();

        AmountRequest request = new AmountRequest(BigDecimal.TEN);

        mockMvc.perform(post("/wallets/{walletId}/withdraw", walletId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void shouldReturnBadRequestWhenIdempotencyKeyIsBlankOnWithdraw() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();

        AmountRequest request = new AmountRequest(BigDecimal.TEN);

        mockMvc.perform(post("/wallets/{walletId}/withdraw", walletId)
                        .header("X-User-Id", userId.toString())
                        .header("Idempotency-Key", " ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void shouldReturnBadRequestWhenAmountIsNegativeOnWithdraw() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        AmountRequest request = new AmountRequest(BigDecimal.valueOf(-10));

        mockMvc.perform(post("/wallets/{walletId}/withdraw", walletId)
                        .header("X-User-Id", userId.toString())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void shouldReturnBadRequestWhenMalformedJsonOnWithdraw() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();

        mockMvc.perform(post("/wallets/{walletId}/withdraw", walletId)
                        .header("X-User-Id", userId.toString())
                        .header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ invalid json }"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void shouldReturnForbiddenWhenServiceThrowsOnWithdraw() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        AmountRequest request = new AmountRequest(BigDecimal.TEN);

        when(transactionService.withdraw(userId, key, walletId, request.amount()))
                .thenThrow(new ForbiddenException("Access denied"));

        mockMvc.perform(post("/wallets/{walletId}/withdraw", walletId)
                        .header("X-User-Id", userId.toString())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldTransferSuccessfully() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID fromWalletId = UUID.randomUUID();
        UUID toWalletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        TransferRequest request = new TransferRequest(
                fromWalletId,
                toWalletId,
                BigDecimal.valueOf(100)
        );

        TransferResponse txResponse = new TransferResponse(
                UUID.randomUUID(),
                fromWalletId,
                toWalletId,
                request.amount()
        );

        ApiResponse<TransferResponse> apiResponse = ApiResponse.success("Transfer successful", txResponse);

        when(transactionService.transfer(userId, key, fromWalletId, toWalletId, request.amount()))
                .thenReturn(apiResponse);

        mockMvc.perform(post("/transfers")
                        .header("X-User-Id", userId.toString())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Transfer successful"))
                .andExpect(jsonPath("$.data.referenceId").value(txResponse.referenceId().toString()))
                .andExpect(jsonPath("$.data.fromWalletId").value(fromWalletId.toString()))
                .andExpect(jsonPath("$.data.toWalletId").value(toWalletId.toString()))
                .andExpect(jsonPath("$.data.amount").value(100));

        verify(transactionService).transfer(userId, key, fromWalletId, toWalletId, request.amount());
    }

    @Test
    void shouldReturnBadRequestWhenUserIdHeaderMissingOnTransfer() throws Exception {
        TransferRequest request = new TransferRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                BigDecimal.TEN
        );

        mockMvc.perform(post("/transfers")
                        .header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void shouldReturnBadRequestWhenIdempotencyKeyMissingOnTransfer() throws Exception {
        UUID userId = UUID.randomUUID();

        TransferRequest request = new TransferRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                BigDecimal.TEN
        );

        mockMvc.perform(post("/transfers")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void shouldReturnBadRequestWhenIdempotencyKeyIsBlankOnTransfer() throws Exception {
        UUID userId = UUID.randomUUID();

        TransferRequest request = new TransferRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                BigDecimal.TEN
        );

        mockMvc.perform(post("/transfers")
                        .header("X-User-Id", userId.toString())
                        .header("Idempotency-Key", " ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void shouldReturnBadRequestWhenAmountIsNegativeOnTransfer() throws Exception {
        UUID userId = UUID.randomUUID();
        String key = "valid-key";

        TransferRequest request = new TransferRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                BigDecimal.valueOf(-10)
        );

        mockMvc.perform(post("/transfers")
                        .header("X-User-Id", userId.toString())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void shouldReturnBadRequestWhenSameWallet() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        TransferRequest request = new TransferRequest(
                walletId,
                walletId,
                BigDecimal.TEN
        );

        when(transactionService.transfer(
                userId,
                key,
                walletId,
                walletId,
                request.amount()
        )).thenThrow(new IllegalArgumentException("Cannot transfer to same wallet"));

        mockMvc.perform(post("/transfers")
                        .header("X-User-Id", userId.toString())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenMalformedJson() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/transfers")
                        .header("X-User-Id", userId.toString())
                        .header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ invalid json }"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void shouldReturnForbiddenWhenServiceThrows() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        TransferRequest request = new TransferRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                BigDecimal.TEN
        );

        when(transactionService.transfer(
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenThrow(new ForbiddenException("Access denied"));

        mockMvc.perform(post("/transfers")
                        .header("X-User-Id", userId.toString())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
