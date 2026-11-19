package com.bcp.banking.account.exception;

/**
 * Regla de negocio violada: 422 (saldo insuficiente, cuenta bloqueada/cerrada).
 */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
