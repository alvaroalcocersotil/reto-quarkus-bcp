package com.bcp.banking.account.exception;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Mapper catch-all RFC 7807 (copilot-instructions §2).
 */
public class GenericExceptionMapper {

    @ServerExceptionMapper
    public Response toResponse(Exception ex) {
        if (ex instanceof WebApplicationException wae) {
            int status = wae.getResponse().getStatus();
            return Response.status(status)
                    .type("application/problem+json")
                    .entity(ProblemDetail.of("http-error", ex.getClass().getSimpleName(),
                            status, ex.getMessage(), null))
                    .build();
        }
        return Response.status(500)
                .type("application/problem+json")
                .entity(ProblemDetail.of("internal-error", "Internal Server Error", 500,
                        "Unexpected error. Contact support with the correlationId.", null))
                .build();
    }
}
