package com.bcp.banking.account.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Respuesta de operación interna: saldo resultante y versión para trazabilidad.
 */
public record OperationResponse(UUID accountId, String accountNumber, BigDecimal balance, Long version) {
}
