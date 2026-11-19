package com.bcp.banking.customer.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * 404 → RFC 7807 (spec 01 §3).
 */
@Provider
public class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {

    @Override
    public jakarta.ws.rs.core.Response toResponse(NotFoundException ex) {
        ProblemDetail body = ProblemDetail.of("customer-not-found", "Not Found", 404, ex.getMessage(), null);
        return jakarta.ws.rs.core.Response.status(404)
                .type("application/problem+json")
                .entity(body)
                .build();
    }
}
