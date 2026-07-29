package com.recargapay.wallet_service.repository;

import com.recargapay.wallet_service.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.wallet.id = :walletId")
    BigDecimal getBalance(Long walletId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.wallet.id = :walletId AND t.createdAt <= :timestamp")
    BigDecimal getBalanceAt(Long walletId, Instant timestamp);
}
