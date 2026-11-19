package com.bcp.banking.transaction.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * SagaException → RFC 7807 con el status de la fase que falló (spec 03 §2).
 */
@Provider
public class SagaExceptionMapper implements jakarta.ws.rs.ext.ExceptionMapper<SagaException> {

    @Override
    public Response toResponse(SagaException ex) {
        ProblemDetail body = ProblemDetail.of(ex.code(), ex.code(), ex.status(), ex.getMessage(), null);
        return jakarta.ws.rs.core.Response.status(ex.status())
                .type("application/problem+json")
                .entity(body)
                .build();
    }
}
