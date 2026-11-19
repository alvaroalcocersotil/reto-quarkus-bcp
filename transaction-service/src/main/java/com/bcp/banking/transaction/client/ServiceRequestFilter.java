package com.bcp.banking.transaction.client;

import com.bcp.banking.transaction.security.ServiceTokenProvider;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.ext.Provider;

import org.slf4j.MDC;

import java.io.IOException;

/**
 * Filtro de salida de los REST Clients: agrega el bearer token de servicio
 * (ROLE_ADMIN) y propaga X-Correlation-ID del MDC (copilot-instructions §4).
 */
@Provider
public class ServiceRequestFilter implements ClientRequestFilter {

    private final jakarta.inject.Provider<ServiceTokenProvider> tokenProvider;

    @jakarta.inject.Inject
    public ServiceRequestFilter(jakarta.inject.Provider<ServiceTokenProvider> tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void filter(ClientRequestContext ctx) {
        try {
            ctx.getHeaders().putSingle("Authorization", "Bearer " + tokenProvider.get().token());
        } catch (Exception e) {
            // sin firma disponible (p.ej. test con mocks): continuar sin token
        }
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            ctx.getHeaders().putSingle("X-Correlation-ID", correlationId);
        }
    }
}
