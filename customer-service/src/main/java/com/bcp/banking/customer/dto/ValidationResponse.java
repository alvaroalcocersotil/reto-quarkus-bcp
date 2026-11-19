package com.bcp.banking.customer.dto;

import com.bcp.banking.customer.domain.CustomerStatus;

import java.util.UUID;

/**
 * Respuesta de validación ligera usada por transaction-service (spec 01 §4).
 */
public record ValidationResponse(UUID customerId, CustomerStatus status, boolean eligibleForTransactions) {
}
