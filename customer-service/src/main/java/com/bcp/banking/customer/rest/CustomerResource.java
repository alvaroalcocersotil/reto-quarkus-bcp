package com.bcp.banking.customer.rest;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.bcp.banking.customer.domain.DocumentType;
import com.bcp.banking.customer.dto.CreateCustomerRequest;
import com.bcp.banking.customer.dto.CustomerResponse;
import com.bcp.banking.customer.dto.UpdateStatusRequest;
import com.bcp.banking.customer.dto.ValidationResponse;
import com.bcp.banking.customer.service.CustomerService;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
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
 * Recurso REST delgado (spec 01 §2). Sin lógica de negocio — delega en CustomerService.
 * Seguridad: USER lectura, ADMIN escritura.
 */
@Path("/api/v1/customers")
@Produces(MediaType.APPLICATION_JSON)
public class CustomerResource {

    @Inject
    CustomerService service;

    @GET
    @Path("/{id}")
    @RolesAllowed({"ROLE_USER", "ROLE_ADMIN"})
    public CustomerResponse get(@PathParam("id") UUID id) {
        return service.getById(id);
    }

    /** GET /api/v1/customers?documentType=&documentNumber= (spec 01 §2). */
    @GET
    @RolesAllowed({"ROLE_USER", "ROLE_ADMIN"})
    public List<CustomerResponse> list(
            @QueryParam("documentType") DocumentType documentType,
            @QueryParam("documentNumber") String documentNumber,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        if (documentType != null && documentNumber != null) {
            return service.findByDocument(documentType, documentNumber);
        }
        return service.list(page, size);
    }

    @POST
    @RolesAllowed("ROLE_ADMIN")
    public Response create(@Valid CreateCustomerRequest request, @Context UriInfo uri) {
        CustomerResponse created = service.create(request);
        return Response.created(uri.getAbsolutePathBuilder().path(created.id().toString()).build())
                .entity(created).build();
    }

    @PATCH
    @Path("/{id}/status")
    @RolesAllowed("ROLE_ADMIN")
    public CustomerResponse updateStatus(@PathParam("id") UUID id, @Valid UpdateStatusRequest request) {
        return service.updateStatus(id, request.status());
    }

    @GET
    @Path("/{id}/validate")
    @RolesAllowed({"ROLE_USER", "ROLE_ADMIN"})
    public ValidationResponse validate(@PathParam("id") UUID id) {
        return service.validate(id);
    }
}
