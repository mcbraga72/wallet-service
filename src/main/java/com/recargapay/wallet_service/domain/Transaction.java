package com.recargapay.wallet_service.domain;

import com.recargapay.wallet_service.enums.TransactionStatus;
import com.recargapay.wallet_service.enums.TransactionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "transactions")
@Entity
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @EqualsAndHashCode.Include
    @Column(name = "public_id", unique = true, nullable = false, updatable = false)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Transaction(
            Wallet wallet,
            BigDecimal amount,
            TransactionType type,
            TransactionStatus status,
            UUID referenceId
    ) {
        if (wallet == null) throw new IllegalArgumentException("wallet is required");
        if (amount == null || amount.signum() == 0) throw new IllegalArgumentException("amount cannot be zero");
        if (type == null) throw new IllegalArgumentException("type is required");
        if (status == null) throw new IllegalArgumentException("status is required");

        this.wallet = wallet;
        this.amount = amount;
        this.type = type;
        this.status = status;
        this.referenceId = referenceId;
    }

    @PrePersist
    public void prePersist() {
        if (this.publicId == null) {
            this.publicId = UUID.randomUUID();
        }

        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}