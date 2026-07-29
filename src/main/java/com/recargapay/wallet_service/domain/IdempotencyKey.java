package com.recargapay.wallet_service.domain;

import com.recargapay.wallet_service.enums.IdempotencyKeyStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "idempotency_keys")
@Entity
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @EqualsAndHashCode.Include
    @Column(name = "idempotency_key", unique = true, nullable = false, updatable = false)
    private UUID idempotencyKey;

    @EqualsAndHashCode.Include
    @Column(name = "user_public_id", unique = true, nullable = false, updatable = false)
    private UUID userPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IdempotencyKeyStatus status;

    @Column(name = "response")
    private String response;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public IdempotencyKey(
            UUID idempotencyKey,
            UUID userPublicId,
            IdempotencyKeyStatus status,
            String response
    ) {
        if (idempotencyKey == null) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }

        if (userPublicId == null) {
            throw new IllegalArgumentException("userPublicId is required");
        }

        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }

        this.idempotencyKey = idempotencyKey;
        this.userPublicId = userPublicId;
        this.status = status;
        this.response = response;
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}