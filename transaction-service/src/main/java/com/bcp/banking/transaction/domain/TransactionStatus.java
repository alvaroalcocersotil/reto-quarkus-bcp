package com.bcp.banking.transaction.domain;

/**
 * Estados del Saga orquestado (spec 03 §2).
 * Flujo feliz:  PENDING → VALIDATION → DEBIT → CREDIT → COMPLETED
 * Compensación: (fallo en CREDIT/validaciones aguas abajo)
 *               COMPENSATION_PENDING → COMPENSATED → FAILED
 * Fallo en DEBIT (sin efectos): FAILED directo.
 */
public enum TransactionStatus {
    PENDING,
    VALIDATION,
    DEBIT,
    CREDIT,
    COMPLETED,
    COMPENSATION_PENDING,
    COMPENSATED,
    FAILED
}
