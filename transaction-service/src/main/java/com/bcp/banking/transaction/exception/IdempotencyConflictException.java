package com.bcp.banking.transaction.exception;

/**
 * Idempotency-Key reutilizada con payload distinto (HTTP 409, spec 03 §2).
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
