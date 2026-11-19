package com.bcp.banking.account.rest;

import java.util.UUID;

import com.bcp.banking.account.dto.OperationRequest;
import com.bcp.banking.account.dto.OperationResponse;
import com.bcp.banking.account.service.AccountService;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Endpoints internos para el orquestador Saga (spec 02 §3).
 * SOLO ROLE_ADMIN (credencial de servicio del transaction-service).
 * - POST /internal/accounts/{id}/debit             → fase DEBIT
 * - POST /internal/accounts/{id}/credit            → fase CREDIT
 * - POST /internal/accounts/{id}/compensate-debit  → compensación del débito
 * - POST /internal/accounts/{id}/compensate-credit → compensación del crédito
 */
@Path("/internal/accounts")
@Consumes(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
@Produces(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
public class InternalAccountResource {

    @Inject
    AccountService service;

    @POST
    @Path("/{id}/debit")
    @RolesAllowed("ROLE_ADMIN")
    public OperationResponse debit(@PathParam("id") UUID id, OperationRequest request) {
        return service.debit(id, request);
    }

    @POST
    @Path("/{id}/credit")
    @RolesAllowed("ROLE_ADMIN")
    public OperationResponse credit(@PathParam("id") UUID id, OperationRequest request) {
        return service.credit(id, request);
    }

    @POST
    @Path("/{id}/compensate-debit")
    @RolesAllowed("ROLE_ADMIN")
    public OperationResponse compensateDebit(@PathParam("id") UUID id, OperationRequest request) {
        return service.compensateDebit(id, request);
    }

    @POST
    @Path("/{id}/compensate-credit")
    @RolesAllowed("ROLE_ADMIN")
    public OperationResponse compensateCredit(@PathParam("id") UUID id, OperationRequest request) {
        return service.compensateCredit(id, request);
    }
}
