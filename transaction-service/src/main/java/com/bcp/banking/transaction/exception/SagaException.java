package com.bcp.banking.transaction.exception;

/**
 * Fallo de negocio en el Saga. `httpStatus` determina el código HTTP final
 * (422 validación/débito, 502 fallas de infraestructura aguas abajo, etc.).
 */
public class SagaException extends RuntimeException {

    private final String code;
    private final int status;
    private final String phase;

    public SagaException(String code, int status, String phase, String message) {
        super(message);
        this.code = code;
        this.status = status;
        this.phase = phase;
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }

    public String phase() {
        return phase;
    }
}
