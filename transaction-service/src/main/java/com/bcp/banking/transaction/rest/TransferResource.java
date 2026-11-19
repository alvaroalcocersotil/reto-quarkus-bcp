package com.bcp.banking.transaction.rest;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.bcp.banking.transaction.domain.Transaction;
import com.bcp.banking.transaction.domain.TransactionRepository;
import com.bcp.banking.transaction.dto.TransferRequest;
import com.bcp.banking.transaction.dto.TransferResponse;
import com.bcp.banking.transaction.exception.IdempotencyConflictException;
import com.bcp.banking.transaction.exception.NotFoundException;
import com.bcp.banking.transaction.service.TransactionStateService;
import com.bcp.banking.transaction.service.TransferSagaOrchestrator;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

/**
 * API pública de transferencias (spec 03 §2).
 * POST /api/v1/transfers con header Idempotency-Key:
 *  - 201 primera vez, 200 replay (misma key + mismo payload), 409 conflicto.
 */
@Path("/api/v1/transfers")
@Produces(MediaType.APPLICATION_JSON)
public class TransferResource {

    @Inject
    TransactionStateService state;
    @Inject
    com.bcp.banking.transaction.domain.TransactionRepository repository;
    @Inject
    TransferSagaOrchestrator orchestrator;

    @POST
    @jakarta.annotation.security.RolesAllowed({"ROLE_USER", "ROLE_ADMIN"})
    public Response create(@Valid TransferRequest request,
                           @jakarta.ws.rs.HeaderParam("Idempotency-Key") String idempotencyKey,
                           @Context UriInfo uri) {
        UUID key = parseKey(idempotencyKey);
        var existing = repository.findByIdempotencyKey(key).orElse(null);
        if (existing != null) {
            if (!matches(existing, request)) {
                throw new com.bcp.banking.transaction.exception.IdempotencyConflictException(
                        "Idempotency-Key %s already used with a different payload".formatted(key));
            }
            return Response.ok(TransferResponse.from(existing)).build(); // 200 replay
        }
        Transaction tx = state.create(request.sourceAccountId(), request.targetAccountId(),
                request.amount(), request.customerId(), key,
                org.slf4j.MDC.get("correlationId"));
        try {
            orchestrator.execute(tx);
        } catch (com.bcp.banking.transaction.exception.SagaException e) {
            // la transacción quedó FAILED/COMPENSATED: se devuelve el problema RFC 7807
            throw e;
        }
        Transaction updated = repository.findById(tx.id); // estado fresco tras el Saga
        return Response.created(uri.getAbsolutePathBuilder().path(tx.id.toString()).build())
                .entity(TransferResponse.from(updated))
                .header("Idempotency-Key", idempotencyKey)
                .build();
    }

    @GET
    @Path("/{id}")
    @jakarta.annotation.security.RolesAllowed({"ROLE_USER", "ROLE_ADMIN"})
    public TransferResponse get(@PathParam("id") UUID id) {
        return repository.findByIdOptional(id)
                .map(TransferResponse::from)
                .orElseThrow(() -> new com.bcp.banking.transaction.exception.NotFoundException(
                        "Transfer " + id + " not found"));
    }

    @GET
    @jakarta.annotation.security.RolesAllowed({"ROLE_USER", "ROLE_ADMIN"})
    public java.util.List<TransferResponse> list(
            @QueryParam("status") String status,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        var query = (status == null || status.isBlank())
                ? repository.findAll().page(safePage, safeSize)
                : repository.find("status", status).page(safePage, safeSize);
        return query.list().stream().map(TransferResponse::from).toList();
    }

    private static UUID parseKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new jakarta.ws.rs.BadRequestException("Idempotency-Key header is required");
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new jakarta.ws.rs.BadRequestException("Idempotency-Key must be a UUID");
        }
    }

    private static boolean matches(Transaction t, TransferRequest r) {
        return t.sourceAccountId.equals(r.sourceAccountId())
                && t.targetAccountId.equals(r.targetAccountId())
                && t.amount.compareTo(r.amount()) == 0;
    }
}
