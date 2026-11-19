package com.bcp.banking.transaction.client;

import com.bcp.banking.transaction.dto.ValidationResponse;

import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Cliente reactivo hacia customer-service (spec 03 §3).
 * Resiliencia: @Retry + @Timeout + @CircuitBreaker.
 */
@RegisterRestClient(configKey = "customer-client")
public interface CustomerClient {

    @Retry(maxRetries = 2, delay = 200)
    @Timeout(2000)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000)
    @GET
    @Path("/api/v1/customers/{id}/validate")
    ValidationResponse validate(@jakarta.ws.rs.PathParam("id") java.util.UUID id);

    @ClientExceptionMapper
    static RuntimeException mapException(jakarta.ws.rs.core.Response response) {
        // Los 4xx del servicio remoto se propagan tal cual para manejo del Saga
        return null;
    }
}
