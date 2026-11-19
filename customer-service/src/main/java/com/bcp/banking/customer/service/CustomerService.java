package com.bcp.banking.customer.service;

import java.util.List;
import java.util.UUID;

import com.bcp.banking.customer.domain.Customer;
import com.bcp.banking.customer.domain.CustomerRepository;
import com.bcp.banking.customer.domain.CustomerStatus;
import com.bcp.banking.customer.domain.DocumentType;
import com.bcp.banking.customer.dto.CreateCustomerRequest;
import com.bcp.banking.customer.dto.CustomerResponse;
import com.bcp.banking.customer.dto.UpdateStatusRequest;
import com.bcp.banking.customer.dto.ValidationResponse;
import com.bcp.banking.customer.exception.BusinessRuleException;
import com.bcp.banking.customer.exception.NotFoundException;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Lógica de negocio de clientes (spec 01). Transacciones SOLO aquí, nunca en el resource.
 */
@ApplicationScoped
public class CustomerService {

    @Inject
    CustomerRepository repository;

    public CustomerResponse getById(UUID id) {
        return repository.findByIdOptional(id)
                .map(CustomerResponse::from)
                .orElseThrow(() -> new NotFoundException("Customer " + id + " not found"));
    }

    public List<CustomerResponse> list(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100); // size <= 100 (spec 01 §2)
        PanacheQuery<Customer> query = repository.findAll().page(safePage, safeSize);
        return query.list().stream().map(CustomerResponse::from).toList();
    }

    public java.util.List<CustomerResponse> findByDocument(DocumentType type, String number) {
        return repository.findByDocument(type, number)
                .map(c -> java.util.List.of(CustomerResponse.from(c)))
                .orElseGet(java.util.List::of);
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest req) {
        // Regla 1: documento único (spec 01 §4.1)
        repository.findByDocument(req.documentType(), req.documentNumber()).ifPresent(c -> {
            throw new BusinessRuleException("duplicate-customer",
                    "Customer with %s %s already exists".formatted(req.documentType(), req.documentNumber()));
        });
        // Regla 2: email único case-insensitive
        repository.findByEmailIgnoreCase(req.email()).ifPresent(c -> {
            throw new BusinessRuleException("duplicate-customer",
                    "Customer with email %s already exists".formatted(req.email()));
        });
        Customer c = new Customer();
        c.firstName = req.firstName();
        c.lastName = req.lastName();
        c.documentType = req.documentType();
        c.documentNumber = req.documentNumber();
        c.email = req.email();
        c.phoneNumber = req.phoneNumber();
        repository.persist(c);
        return CustomerResponse.from(c);
    }

    @Transactional
    public CustomerResponse updateStatus(UUID id, CustomerStatus newStatus) {
        Customer c = repository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Customer " + id + " not found"));
        c.status = newStatus;
        return CustomerResponse.from(c);
    }

    /**
     * Validación ligera para el orquestador (spec 01 §4).
     * 422 + customer-not-active si el cliente no está ACTIVE.
     */
    public ValidationResponse validate(UUID id) {
        Customer c = repository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Customer " + id + " not found"));
        if (c.status != CustomerStatus.ACTIVE) {
            throw new BusinessRuleException("customer-not-active",
                    "Customer %s is %s, not eligible for transactions".formatted(id, c.status));
        }
        return new ValidationResponse(c.id, c.status, true);
    }
}
