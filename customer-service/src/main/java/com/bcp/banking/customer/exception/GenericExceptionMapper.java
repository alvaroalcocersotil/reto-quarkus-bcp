package com.bcp.banking.customer.exception;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Mapper catch-all: 500 sin filtrar stacktrace al cliente (copilot-instructions §2).
 * WebApplicationException no controladas respetan su status original.
 */
public class GenericExceptionMapper {

    @ServerExceptionMapper
    public Response toResponse(Exception ex) {
        if (ex instanceof WebApplicationException wae) {
            int status = wae.getResponse().getStatus();
            ProblemDetail body = ProblemDetail.of("http-error",
                    ex.getClass().getSimpleName(), status, ex.getMessage(), null);
            return Response.status(status).type("application/problem+json").entity(body).build();
        }
        ProblemDetail body = ProblemDetail.of("internal-error", "Internal Server Error", 500,
                "Unexpected error. Contact support with the correlationId.", null);
        return Response.status(500).type("application/problem+json").entity(body).build();
    }
}
