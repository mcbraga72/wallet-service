package com.recargapay.wallet_service.service;

import com.recargapay.wallet_service.domain.Transaction;
import com.recargapay.wallet_service.domain.Wallet;
import com.recargapay.wallet_service.dto.response.ApiResponse;
import com.recargapay.wallet_service.dto.response.TransactionResponse;
import com.recargapay.wallet_service.enums.TransactionStatus;
import com.recargapay.wallet_service.enums.TransactionType;
import com.recargapay.wallet_service.repository.TransactionRepository;
import com.recargapay.wallet_service.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TransactionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void shouldNotDuplicateDepositWhenSameIdempotencyKey() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(100.00);

        Wallet wallet = walletRepository.save(new Wallet(userId));

        // Act
        ApiResponse<TransactionResponse> first = transactionService.deposit(userId, key, wallet.getPublicId(), amount);
        ApiResponse<TransactionResponse> second = transactionService.deposit(userId, key, wallet.getPublicId(), amount);

        // Assert
        assertEquals(first.data().transactionId(), second.data().transactionId());

        BigDecimal balance = transactionRepository.getBalance(wallet.getId());
        assertEquals(0, balance.compareTo(amount));
    }

    @Test
    void shouldHandleConcurrentTransfersWithoutDoubleSpend() throws Exception {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID key1 = UUID.randomUUID();
        UUID key2 = UUID.randomUUID();

        Wallet from = walletRepository.save(new Wallet(userId));
        Wallet to = walletRepository.save(new Wallet(UUID.randomUUID()));

        transactionRepository.save(new Transaction(
                from,
                BigDecimal.valueOf(100),
                TransactionType.DEPOSIT,
                TransactionStatus.SUCCESS,
                null
        ));

        BigDecimal transferAmount = BigDecimal.valueOf(100);

        // Act
        List<Future<Boolean>> results;

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> task1 = () -> {
                try {
                    transactionService.transfer(
                            userId,
                            key1,
                            from.getPublicId(),
                            to.getPublicId(),
                            transferAmount
                    );
                    return true;
                } catch (Exception e) {
                    return false;
                }
            };

            Callable<Boolean> task2 = () -> {
                try {
                    transactionService.transfer(
                            userId,
                            key2,
                            from.getPublicId(),
                            to.getPublicId(),
                            transferAmount
                    );
                    return true;
                } catch (Exception e) {
                    return false;
                }
            };

            results = executor.invokeAll(List.of(task1, task2));

            executor.shutdown();
        }

        // Assert
        long successCount = results.stream()
                .map(f -> {
                    try {
                        return f.get();
                    } catch (Exception e) {
                        return false;
                    }
                })
                .filter(Boolean::booleanValue)
                .count();

        assertEquals(1, successCount);

        BigDecimal finalBalance = transactionRepository.getBalance(from.getId());

        assertEquals(0, finalBalance.compareTo(BigDecimal.ZERO));
    }
}