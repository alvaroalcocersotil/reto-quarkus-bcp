package com.bcp.banking.account.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * 404 → RFC 7807 (spec 02 §3).
 */
@Provider
public class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {

    @Override
    public Response toResponse(NotFoundException ex) {
        ProblemDetail body = ProblemDetail.of("account-not-found", "Not Found", 404, ex.getMessage(), null);
        return Response.status(404).type("application/problem+json").entity(body).build();
    }
}
