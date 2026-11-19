package com.bcp.banking.transaction.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * 409 por Idempotency-Key reutilizada con payload distinto (spec 03 §2).
 */
@Provider
public class IdempotencyConflictExceptionMapper implements ExceptionMapper<IdempotencyConflictException> {

    @Override
    public Response toResponse(IdempotencyConflictException ex) {
        ProblemDetail body = ProblemDetail.of("idempotency-conflict", "Conflict", 409, ex.getMessage(), null);
        return Response.status(409).type("application/problem+json").entity(body).build();
    }
}
