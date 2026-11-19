package com.bcp.banking.account.domain;

/**
 * Estado de la cuenta (spec 02 §1). Solo ACTIVE acepta débitos/créditos.
 */
public enum AccountStatus {
    ACTIVE, BLOCKED, CLOSED
}
