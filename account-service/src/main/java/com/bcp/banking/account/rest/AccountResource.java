package com.bcp.banking.account.rest;

import java.util.List;
import java.util.UUID;

import com.bcp.banking.account.dto.AccountResponse;
import com.bcp.banking.account.dto.CreateAccountRequest;
import com.bcp.banking.account.dto.OperationRequest;
import com.bcp.banking.account.dto.OperationResponse;
import com.bcp.banking.account.service.AccountService;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
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
 * API pública de cuentas (spec 02 §2). Lectura USER/ADMIN, escritura ADMIN.
 */
@Path("/api/v1/accounts")
@Produces(MediaType.APPLICATION_JSON)
public class AccountResource {

    @Inject
    AccountService service;

    @GET
    @Path("/{id}")
    @RolesAllowed({"ROLE_USER", "ROLE_ADMIN"})
    public AccountResponse get(@PathParam("id") UUID id) {
        return service.getById(id);
    }

    /** GET /api/v1/accounts/{id}/balance — usado por transaction-service para validar. */
    @GET
    @Path("/{id}/balance")
    @RolesAllowed({"ROLE_USER", "ROLE_ADMIN"})
    public OperationResponse getBalance(@PathParam("id") UUID id) {
        var a = service.getById(id);
        return new OperationResponse(a.id(), a.accountNumber(), a.balance(), null);
    }

    @GET
    @RolesAllowed({"ROLE_USER", "ROLE_ADMIN"})
    public List<AccountResponse> list(
            @QueryParam("customerId") UUID customerId,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        return service.list(page, size);
    }

    @POST
    @RolesAllowed("ROLE_ADMIN")
    public Response create(@Valid CreateAccountRequest request,
                           @Context UriInfo uri) {
        AccountResponse created = service.create(request.customerId(), request.initialBalance(),
                request.currency());
        return Response
                .created(uri.getAbsolutePathBuilder().path(created.id().toString()).build())
                .entity(created).build();
    }
}
