package com.bcp.banking.transaction.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Respuesta de operaciones internas del account-service.
 */
public record OperationResponse(UUID accountId, String accountNumber, BigDecimal balance, Long version) {
}
