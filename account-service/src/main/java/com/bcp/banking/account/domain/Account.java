package com.bcp.banking.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Cuenta bancaria (spec 02 §1).
 * REGLA CRÍTICA: @Version (optimistic locking) obligatorio — sin débitos concurrentes perdidos.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    public UUID id;

    /** Número de negocio, formato ACC-XXXXXX (spec 02 §1). */
    @NotBlank
    @Size(max = 20)
    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    public String accountNumber;

    @NotNull
    @Column(name = "customer_id", nullable = false)
    public UUID customerId;

    @NotNull
    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal balance = BigDecimal.ZERO;

    /** Moneda ISO-4217, siempre PEN en este reto (spec 02 §4). */
    @NotNull
    @Size(min = 3, max = 3)
    @Column(nullable = false, length = 3)
    public String currency = "PEN";

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public AccountStatus status = AccountStatus.ACTIVE;

    /** Optimistic locking: el commit falla con OptimisticLockException si hubo modificación concurrente. */
    @Version
    @Column(nullable = false)
    public Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    public Instant updatedAt;

    /** Suficiente saldo para debitar (incluye saldo exacto, sin sobregiro). */
    public boolean hasSufficientBalance(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }
}
