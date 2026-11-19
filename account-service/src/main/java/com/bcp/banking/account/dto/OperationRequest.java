package com.bcp.banking.account.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Request de operación (débito/crédito/compensación) invocada por el orquestador (spec 02 §3).
 */
public record OperationRequest(
        @NotNull UUID transactionId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount) {
}
