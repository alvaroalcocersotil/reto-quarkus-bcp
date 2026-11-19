package com.bcp.banking.transaction.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Transferencia gestionada por el Saga orquestado (spec 03 §2).
 * idempotencyKey UNIQUE: soporta 201 / 200 replay / 409 conflicto.
 */
@Entity
@Table(name = "transfers")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    public UUID id;

    @NotNull
    @Column(name = "source_account_id", nullable = false)
    public UUID sourceAccountId;

    @NotNull
    @Column(name = "target_account_id", nullable = false)
    public UUID targetAccountId;

    @NotNull
    @Column(nullable = false, precision = 14, scale = 2)
    public java.math.BigDecimal amount;

    @NotNull
    @Column(nullable = false, length = 3)
    public String currency = "PEN";

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    public TransactionStatus status = TransactionStatus.PENDING;

    /** Última fase ejecutada: VALIDATION / DEBIT / CREDIT (spec 03 §2). */
    @Column(name = "last_phase", length = 20)
    public String lastPhase;

    /** Clave de idempotencia del cliente (header Idempotency-Key). */
    @NotNull
    @Column(name = "idempotency_key", nullable = false, unique = true)
    public UUID idempotencyKey;

    @Column(name = "customer_id")
    public UUID customerId;

    @Column(name = "failure_reason")
    public String failureReason;

    @Column(name = "correlation_id")
    public String correlationId;

    @Version
    @Column(nullable = false)
    public Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    public Instant updatedAt;
}
