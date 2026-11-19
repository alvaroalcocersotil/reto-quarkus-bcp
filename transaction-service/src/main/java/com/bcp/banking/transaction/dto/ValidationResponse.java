package com.bcp.banking.transaction.dto;

import java.util.UUID;

/**
 * Respuesta de validación de cliente (contrato con customer-service, spec 01 §4).
 */
public record ValidationResponse(UUID customerId, CustomerStatusRef status, boolean eligibleForTransactions) {

    /** Enum espejo mínimo del CustomerStatus remoto. */
    public enum CustomerStatusRef {
        ACTIVE, SUSPENDED, CLOSED
    }
}