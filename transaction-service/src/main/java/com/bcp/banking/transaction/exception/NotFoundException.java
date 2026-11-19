package com.bcp.banking.transaction.exception;

/**
 * Transferencia inexistente (HTTP 404).
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
