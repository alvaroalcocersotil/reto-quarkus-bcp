package com.bcp.banking.customer.domain;

/**
 * Estado del cliente. Solo ACTIVE es elegible para transacciones (spec 01 §4).
 */
public enum CustomerStatus {
    ACTIVE, SUSPENDED, CLOSED
}
