package com.bcp.banking.transaction.client;

import java.math.BigDecimal;
import java.util.UUID;

import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import com.bcp.banking.transaction.dto.OperationResponse;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import com.bcp.banking.transaction.dto.OperationRequest;
import com.bcp.banking.transaction.dto.OperationResponse;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

/**
 * REST Client hacia account-service (spec 03 §3). Endpoints /internal/** con ROLE_ADMIN.
 * Resiliencia: @Retry + @Timeout en cada leg del Saga; la compensación tiene más reintentos.
 */
@RegisterRestClient(configKey = "account-client")
public interface AccountClient {

    @Retry(maxRetries = 2, delay = 200)
    @Timeout(3000)
    @GET
    @Path("/internal/accounts/{id}/balance")
    OperationResponse getBalance(@jakarta.ws.rs.PathParam("id") UUID id);

    @Retry(maxRetries = 2, delay = 100)
    @POST
    @Path("/internal/accounts/{id}/debit")
    @Consumes(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
    OperationResponse debit(@jakarta.ws.rs.PathParam("id") UUID id, OperationRequest request);

    @Retry(maxRetries = 1, delay = 100)
    @POST
    @Path("/internal/accounts/{id}/credit")
    @Consumes(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
    OperationResponse credit(@jakarta.ws.rs.PathParam("id") UUID id, OperationRequest request);

    /** Compensación crítica: 3 reintentos (spec 03 §2). */
    @Retry(maxRetries = 3, delay = 200)
    @Timeout(5000)
    @POST
    @Path("/internal/accounts/{id}/compensate-debit")
    @Consumes(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
    OperationResponse compensateDebit(@jakarta.ws.rs.PathParam("id") UUID id, OperationRequest request);

    @Retry(maxRetries = 2, delay = 200)
    @POST
    @Path("/internal/accounts/{id}/compensate-credit")
    @Consumes(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
    OperationResponse compensateCredit(@jakarta.ws.rs.PathParam("id") UUID id, OperationRequest request);
}

