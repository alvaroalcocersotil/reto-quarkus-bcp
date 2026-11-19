package com.bcp.banking.account.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * 422 para reglas de negocio (spec 02 §3): saldo insuficiente, cuenta bloqueada.
 */
@Provider
public class BusinessRuleExceptionMapper implements ExceptionMapper<BusinessRuleException> {

    @Override
    public jakarta.ws.rs.core.Response toResponse(BusinessRuleException ex) {
        ProblemDetail body = ProblemDetail.of(ex.code(), ex.code(), 422, ex.getMessage(), null);
        return jakarta.ws.rs.core.Response.status(422)
                .type("application/problem+json")
                .entity(body)
                .build();
    }
}
