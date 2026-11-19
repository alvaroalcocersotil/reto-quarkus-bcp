package com.bcp.banking.transaction.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Request de transferencia (spec 03 §2). Requiere header Idempotency-Key (UUID).
 */
public record TransferRequest(
        @NotNull UUID sourceAccountId,
        @NotNull UUID targetAccountId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotNull UUID customerId) {
}

