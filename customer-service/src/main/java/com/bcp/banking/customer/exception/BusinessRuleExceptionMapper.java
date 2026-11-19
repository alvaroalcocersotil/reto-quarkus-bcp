package com.bcp.banking.customer.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Mapper global para reglas de negocio → RFC 7807 (spec 01 §3).
 * 409 para duplicados, 422 para estados inválidos.
 */
@Provider
public class BusinessRuleExceptionMapper implements ExceptionMapper<BusinessRuleException> {

    @Override
    public jakarta.ws.rs.core.Response toResponse(BusinessRuleException ex) {
        int status = ex.isConflict() ? 409 : ex.isUnprocessable() ? 422 : 400;
        ProblemDetail body = ProblemDetail.of(ex.code(), ex.code(), status, ex.getMessage(), null);
        return jakarta.ws.rs.core.Response.status(status)
                .type("application/problem+json")
                .entity(body)
                .build();
    }
}
