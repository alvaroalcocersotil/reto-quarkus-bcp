package com.bcp.banking.account.observability;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

/**
 * Propagación de X-Correlation-ID (copilot-instructions §4).
 */
@Provider
@Priority(Priorities.AUTHENTICATION - 100)
public class CorrelationIdFilter implements jakarta.ws.rs.container.ContainerRequestFilter,
        jakarta.ws.rs.container.ContainerResponseFilter {

    public static final String HEADER = "X-Correlation-ID";
    private static final String MDC_KEY = "correlationId";

    @Override
    public void filter(ContainerRequestContext ctx) {
        String correlationId = ctx.getHeaderString(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        String correlationId = MDC.get(MDC_KEY);
        if (correlationId != null && !response.getHeaders().containsKey(HEADER)) {
            response.getHeaders().putSingle(HEADER, correlationId);
        }
        MDC.remove(MDC_KEY);
    }
}
