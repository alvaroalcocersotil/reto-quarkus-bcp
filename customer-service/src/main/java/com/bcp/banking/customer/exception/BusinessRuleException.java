package com.bcp.banking.customer.exception;

/**
 * Excepción de regla de negocio con código RFC 7807 asociado.
 * El mapper decide el HTTP final según el código (spec 01 §3).
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

    /** true si la regla corresponde a un conflicto de unicidad (HTTP 409). */
    public boolean isConflict() {
        return "duplicate-customer".equals(code);
    }

    /** true si la regla corresponde a un estado de negocio inválido (HTTP 422). */
    public boolean isUnprocessable() {
        return "customer-not-active".equals(code);
    }
}
