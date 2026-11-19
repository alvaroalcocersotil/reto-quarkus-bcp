package com.bcp.banking.transaction.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Request de operación interna hacia account-service (spec 03 §3).
 */
public record OperationRequest(@jakarta.validation.constraints.NotNull UUID transactionId,
                               @NotNull @DecimalMin("0.01") BigDecimal amount) {
}
