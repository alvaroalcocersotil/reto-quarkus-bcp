package com.bcp.banking.account.exception;

/**
 * Cuenta inexistente (HTTP 404).
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
